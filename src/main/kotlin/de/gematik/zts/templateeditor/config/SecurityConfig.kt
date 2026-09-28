/*
 * Copyright (Change Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 */

package de.gematik.zts.templateeditor.config

import de.gematik.zts.templateeditor.domain.auth.AuthSessionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono

/**
 * Security configuration for the application, including authentication and authorization settings.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    private data class ProxyBearerCredentials(
        val token: String,
        val user: String?,
        val email: String?,
        val groups: List<String>,
    )

    @Bean
    fun reactiveAuthenticationManager(): ReactiveAuthenticationManager =
        ReactiveAuthenticationManager { authentication ->
            val credentials = authentication.credentials as? ProxyBearerCredentials
                ?: return@ReactiveAuthenticationManager Mono.empty()

            if (credentials.token.isBlank()) {
                return@ReactiveAuthenticationManager Mono.error(BadCredentialsException("Missing bearer token"))
            }

            val principal = credentials.user?.takeIf { it.isNotBlank() }
                ?: credentials.email?.takeIf { it.isNotBlank() }
                ?: "oauth2-proxy-user"

            val auth = UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            auth.details = AuthSessionDetails(
                accessToken = credentials.token,
                user = credentials.user,
                email = credentials.email,
                groups = credentials.groups,
            )

            Mono.just(auth)
        }

    /**
     * AuthenticationWebFilter that extracts Bearer tokens from the Authorization header.
     */
    @Bean
    fun bearerAuthFilter(manager: ReactiveAuthenticationManager): AuthenticationWebFilter =
        AuthenticationWebFilter(manager).apply {
            setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance())

            setServerAuthenticationConverter { exchange ->
                val forwardedAccessToken = exchange.request.headers
                    .getFirst(HEADER_FORWARDED_ACCESS_TOKEN)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val authorizationHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

                if (authorizationHeader != null && authorizationHeader.length > MAX_AUTHORIZATION_HEADER_LENGTH) {
                    return@setServerAuthenticationConverter Mono.empty()
                }

                val authorizationBearerToken = authorizationHeader
                    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.substringAfter(' ')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val token = forwardedAccessToken ?: authorizationBearerToken
                    ?: return@setServerAuthenticationConverter Mono.empty()

                val groups = exchange.request.headers[HEADER_FORWARDED_GROUPS]
                    ?.flatMap { value -> value.split(',', ';') }
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

                Mono.just(
                    UsernamePasswordAuthenticationToken(
                        null,
                        ProxyBearerCredentials(
                            token = token,
                            user = exchange.request.headers.getFirst(HEADER_FORWARDED_USER),
                            email = exchange.request.headers.getFirst(HEADER_FORWARDED_EMAIL),
                            groups = groups,
                        ),
                    ),
                )
            }
        }

    /**
     * SecurityWebFilterChain that defines security rules for HTTP requests.
     */
    @Bean
    fun securityChain(
        http: ServerHttpSecurity,
        bearerAuthFilter: AuthenticationWebFilter,
    ): SecurityWebFilterChain {
        val openPaths: ServerWebExchangeMatcher = ServerWebExchangeMatchers.pathMatchers(
            "/actuator/**",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/ping",
        )

        val options: ServerWebExchangeMatcher =
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**")

        val securedMatcher =
            AndServerWebExchangeMatcher(
                ServerWebExchangeMatchers.anyExchange(),
                NegatedServerWebExchangeMatcher(openPaths),
                NegatedServerWebExchangeMatcher(options),
            )

        bearerAuthFilter.setRequiresAuthenticationMatcher(securedMatcher)

        return http
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange {
                it
                    .matchers(options, openPaths)
                    .permitAll()
                    .anyExchange()
                    .authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .addFilterAt(bearerAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    private companion object {
        private const val MAX_AUTHORIZATION_HEADER_LENGTH = 10_000
        private const val HEADER_FORWARDED_ACCESS_TOKEN = "X-Forwarded-Access-Token"
        private const val HEADER_FORWARDED_USER = "X-Forwarded-User"
        private const val HEADER_FORWARDED_EMAIL = "X-Forwarded-Email"
        private const val HEADER_FORWARDED_GROUPS = "X-Forwarded-Groups"
    }
}
