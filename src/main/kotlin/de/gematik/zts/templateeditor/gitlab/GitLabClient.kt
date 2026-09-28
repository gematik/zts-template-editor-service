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

package de.gematik.zts.templateeditor.gitlab

import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.config.HttpProperties
import de.gematik.zts.templateeditor.domain.auth.AuthSessionDetails
import de.gematik.zts.templateeditor.logging.logger
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.util.retry.Retry
import java.net.URI
import java.time.Duration

@Component
class GitLabClient(
    private val gitLabProperties: GitLabProperties,
    private val httpBuilder: WebClient.Builder = WebClient.builder(),
    private val httpProperties: HttpProperties,
) {
    private val log by logger()

    // Cache the base WebClient per baseUrl to avoid rebuilding on every request
    // while supporting different base URLs (e.g. /api/v4 vs /oauth/userinfo)
    private val cachedClients = java.util.concurrent.ConcurrentHashMap<String, WebClient>()

    private fun getOrCreateBaseClient(baseUrl: String): WebClient =
        cachedClients.computeIfAbsent(baseUrl) {
            val noRedirectConnector = ReactorClientHttpConnector(
                HttpClient.create().followRedirect(false),
            )

            log.info("Creating GitLab WebClient for baseUrl={}", baseUrl)

            httpBuilder.clone()
                .clientConnector(noRedirectConnector)
                .baseUrl(baseUrl)
                .build()
        }

    /**
     * returns api-v4-Endpoint {provider}/api/v4/{...}.
     * Cached — the issuer URI never changes at runtime.
     */
    private val cachedApiBase: Mono<URI> by lazy {
        Mono.just(
            UriComponentsBuilder
                .fromUriString(gitLabProperties.baseUrl)
                .pathSegment("api", "v4")
                .build()
                .toUri(),
        ).cache()
    }

    fun apiBase(): Mono<URI> = cachedApiBase

    fun getIssuerURI(): Mono<String> =
        Mono.just(gitLabProperties.baseUrl)

    private val cachedUserInfoURI: Mono<URI> by lazy {
        Mono.just(
            UriComponentsBuilder
                .fromUriString(gitLabProperties.baseUrl)
                .path(gitLabProperties.userInfoPath)
                .build()
                .toUri(),
        ).cache()
    }

    fun getUserInfoURI(): Mono<URI> = cachedUserInfoURI

    fun withValidAccessToken(): Mono<String> =
        ReactiveSecurityContextHolder
            .getContext()
            .switchIfEmpty(Mono.error(IllegalStateException("no auth")))
            .flatMap { ctx ->
                val details = ctx.authentication?.details as? AuthSessionDetails
                    ?: return@flatMap Mono.error(IllegalStateException("no oauth2-proxy auth details"))

                if (details.accessToken.isBlank()) {
                    return@flatMap Mono.error(IllegalStateException("empty oauth2-proxy access token"))
                }

                Mono.just(details.accessToken)
            }

    private data class AuthorizedRequest(
        val client: WebClient,
        val token: String,
    )

    private fun authorizedClient(apiBase: Mono<URI> = apiBase()): Mono<AuthorizedRequest> =
        Mono.zip(apiBase, withValidAccessToken()).map { tuple ->
            val baseUrl = tuple.t1.toString()
            val token = tuple.t2
            AuthorizedRequest(getOrCreateBaseClient(baseUrl), token)
        }

    private fun WebClient.RequestHeadersSpec<*>.retrieveGitLabJson(token: String): WebClient.ResponseSpec =
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .handleGitLabStatus()

    private fun WebClient.ResponseSpec.handleGitLabStatus(): WebClient.ResponseSpec =
        onStatus({ it.is3xxRedirection }) { resp ->
            resp.bodyToMono<String>().defaultIfEmpty("")
                .map { body ->
                    GitLabHttpException(
                        resp.statusCode().value(),
                        "Unexpected redirect. Body: ${body.take(200)}",
                    )
                }
        }.onStatus({ it.isError }) { resp ->
            resp
                .bodyToMono<String>()
                .defaultIfEmpty("")
                .map { body -> GitLabHttpException(resp.statusCode().value(), body) }
        }

    fun <T : Any> getFlux(
        apiBase: Mono<URI> = apiBase(),
        clazz: Class<T>,
        query: (UriBuilder) -> UriBuilder = { it },
    ): Flux<T> =
        authorizedClient(apiBase)
            .flatMapMany { (client, token) ->
                client
                    .get()
                    .uri { builder -> query(builder).build() }
                    .retrieveGitLabJson(token)
                    .bodyToFlux(clazz)
            }.timeout(Duration.ofSeconds(httpProperties.timeout))
            .retryWhen(retrySpec())

    fun <T : Any> postMono(
        clazz: Class<T>,
        query: (UriBuilder) -> UriBuilder,
        body: Any,
    ): Mono<T> =
        authorizedClient()
            .flatMap { (client, token) ->
                client
                    .post()
                    .uri { builder -> query(builder).build() }
                    .bodyValue(body)
                    .retrieveGitLabJson(token)
                    .bodyToMono(clazz)
            }.timeout(Duration.ofSeconds(httpProperties.timeout))

    /**
     * Streaming POST: sends a pre-built JSON body as a [Flux] of [DataBuffer] chunks.
     * This avoids holding the entire request payload in heap — ideal for large file commits.
     */
    fun <T : Any> postMonoStreaming(
        clazz: Class<T>,
        query: (UriBuilder) -> UriBuilder,
        bodyStream: Flux<DataBuffer>,
    ): Mono<T> =
        authorizedClient()
            .flatMap { (client, token) ->
                client
                    .post()
                    .uri { builder -> query(builder).build() }
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyStream, DataBuffer::class.java)
                    .retrieveGitLabJson(token)
                    .bodyToMono(clazz)
            }.timeout(Duration.ofSeconds(httpProperties.timeout))

    fun <T : Any> putMono(
        clazz: Class<T>,
        query: (UriBuilder) -> UriBuilder,
        body: Any,
    ): Mono<T> =
        authorizedClient()
            .flatMap { (client, token) ->
                client
                    .put()
                    .uri { builder -> query(builder).build() }
                    .bodyValue(body)
                    .retrieveGitLabJson(token)
                    .bodyToMono(clazz)
            }.timeout(Duration.ofSeconds(httpProperties.timeout))

    fun <T : Any> deleteMono(
        clazz: Class<T>,
        query: (UriBuilder) -> UriBuilder,
    ): Mono<T> =
        authorizedClient()
            .flatMap { (client, token) ->
                client
                    .delete()
                    .uri { builder -> query(builder).build() }
                    .retrieveGitLabJson(token)
                    .bodyToMono(clazz)
            }.timeout(Duration.ofSeconds(httpProperties.timeout))

    fun fetchFileTextInSingleLine(
        projectId: String,
        ref: String,
        path: String,
    ): Mono<String> = fetchFileText(projectId, ref, path, "")

    fun fetchFileTextLines(
        projectId: String,
        ref: String,
        path: String,
    ): Mono<String> = fetchFileText(projectId, ref, path, "\n")

    /**
     * Fetches file text using StringBuilder-based collect to avoid the double-copy
     * from collectList() + joinToString().
     */
    private fun fetchFileText(
        projectId: String,
        ref: String,
        path: String,
        separator: String,
    ): Mono<String> =
        getFlux(clazz = String::class.java) { uri ->
            uri
                .pathSegment("projects", projectId, "repository", "files", path, "raw")
                .queryParam("ref", ref)
        }.collect(
            { StringBuilder() },
            { sb, part ->
                if (separator.isNotEmpty() && sb.isNotEmpty()) sb.append(separator)
                sb.append(part)
            },
        ).map { sb -> sb.toString() }
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) Mono.empty() else Mono.error(ex)
            }
}

private fun retrySpec(): Retry =
    Retry
        .backoff(1, Duration.ofMillis(750))
        .maxBackoff(Duration.ofSeconds(3))
        .jitter(0.3)
        .filter { err ->
            when (err) {
                is WebClientRequestException -> true
                is GitLabHttpException -> err.status in setOf(429, 502, 503, 504)
                else -> false
            }
        }.onRetryExhaustedThrow { _, signal -> signal.failure() }

class GitLabHttpException(
    val status: Int,
    message: String,
) : RuntimeException(
    "GitLab HTTP $status: ${message.take(500)}",
)
