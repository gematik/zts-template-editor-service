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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigWebTest {
    @Autowired lateinit var webTestClient: WebTestClient

    @Test
    fun `secured endpoint without auth returns 401 without basic challenge`() {
        webTestClient
            .get()
            .uri("/secured/ping")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
    }

    @Test
    fun `secured endpoint accepts oauth2-proxy access token header`() {
        webTestClient
            .get()
            .uri("/secured/ping")
            .header("X-Forwarded-Access-Token", "gitlab-access-token")
            .header("X-Forwarded-User", "fabian")
            .header("X-Forwarded-Email", "fabian@example.org")
            .header("X-Forwarded-Groups", "reviewers, dev/publishers")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { assertThat(it).isEqualTo("pong-secured") }
    }

    @Test
    fun `secured endpoint requires auth header on every request`() {
        webTestClient
            .get()
            .uri("/secured/ping")
            .header("X-Forwarded-Access-Token", "gitlab-access-token")
            .exchange()
            .expectStatus().isOk

        webTestClient
            .get()
            .uri("/secured/ping")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `secured endpoint still accepts Authorization bearer as fallback`() {
        webTestClient
            .get()
            .uri("/secured/ping")
            .headers { it.setBearerAuth("local-test-token") }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { assertThat(it).isEqualTo("pong-secured") }
    }

    @Test
    fun `secured endpoint prefers forwarded access token over Authorization bearer`() {
        webTestClient
            .get()
            .uri("/secured/token")
            .headers { it.setBearerAuth("authorization-token") }
            .header("X-Forwarded-Access-Token", "forwarded-token")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { assertThat(it).isEqualTo("forwarded-token") }
    }

    @Test
    fun `preflight with wrong origin returns 403`() {
        val filter = corsFilter()

        val req = MockServerHttpRequest
            .method(HttpMethod.OPTIONS, "/secured/ping")
            .header(HttpHeaders.ORIGIN, "http://evil.local:3000")
            .header("Access-Control-Request-Method", "GET")
            .build()

        val exchange = MockServerWebExchange.from(req)
        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    private fun corsFilter(): CorsWebFilter {
        val cfg = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:5173")
            allowedMethods = listOf("GET", "POST", "DELETE")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }
        val src = UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", cfg)
        }
        return CorsWebFilter(src)
    }
}

@RestController
class TestPingController(
    private val gitLabClient: de.gematik.zts.templateeditor.gitlab.GitLabClient,
) {
    @GetMapping("/secured/ping")
    fun secured() = "pong-secured"

    @GetMapping("/secured/token")
    fun token(): Mono<String> = gitLabClient.withValidAccessToken()
}
