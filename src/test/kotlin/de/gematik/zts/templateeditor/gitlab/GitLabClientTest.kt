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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.config.HttpProperties
import de.gematik.zts.templateeditor.domain.auth.AuthSessionDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.InetSocketAddress
import java.net.URI

class GitLabClientTest {
    private lateinit var server: HttpServer
    private var response = TestResponse()
    private val requests = mutableListOf<CapturedRequest>()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `apiBase returns GitLab api v4 base url`() {
        assertThat(client().apiBase().block()).isEqualTo(URI("https://gitlab.example.com/api/v4"))
    }

    @Test
    fun `getUserInfoURI returns configured GitLab userinfo url`() {
        assertThat(client().getUserInfoURI().block()).isEqualTo(URI("https://gitlab.example.com/oauth/userinfo"))
    }

    @Test
    fun `getIssuerURI returns configured GitLab base url`() {
        assertThat(client().getIssuerURI().block()).isEqualTo("https://gitlab.example.com")
    }

    @Test
    fun `withValidAccessToken returns token from oauth2 proxy auth details`() {
        val token = client().withValidAccessToken()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth()))
            .block()

        assertThat(token).isEqualTo("gitlab-access-token")
    }

    @Test
    fun `withValidAccessToken fails without auth`() {
        assertThatThrownBy { client().withValidAccessToken().block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("no auth")
    }

    @Test
    fun `withValidAccessToken fails when auth details are missing`() {
        val auth = UsernamePasswordAuthenticationToken("fabian", null, emptyList())

        assertThatThrownBy {
            client().withValidAccessToken()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("no oauth2-proxy auth details")
    }

    @Test
    fun `withValidAccessToken fails when access token is blank`() {
        assertThatThrownBy {
            client().withValidAccessToken()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(accessToken = " ")))
                .block()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("empty oauth2-proxy access token")
    }

    @Test
    fun `getFlux sends bearer token and reads response`() {
        response = TestResponse(body = """["one","two"]""")

        val result = client(serverBaseUrl()).getFlux(clazz = String::class.java) { uri ->
            uri.pathSegment("projects").queryParam("q", "template")
        }.withAuth().collectList().block()

        assertThat(result).containsExactly("[\"one\",\"two\"]")
        assertThat(requests.single()).isEqualTo(
            CapturedRequest(
                method = "GET",
                path = "/api/v4/projects?q=template",
                authorization = "Bearer gitlab-access-token",
                accept = MediaType.APPLICATION_JSON_VALUE,
                contentType = null,
                body = "",
            ),
        )
    }

    @Test
    fun `postMono sends bearer token and reads response`() {
        response = TestResponse(body = "\"created\"")

        val result = client(serverBaseUrl()).postMono(
            clazz = String::class.java,
            query = { uri -> uri.pathSegment("projects") },
            body = mapOf("name" to "template"),
        ).withAuth().block()

        assertThat(result).isEqualTo("\"created\"")
        assertThat(requests.single().method).isEqualTo("POST")
        assertThat(requests.single().path).isEqualTo("/api/v4/projects")
        assertThat(requests.single().authorization).isEqualTo("Bearer gitlab-access-token")
        assertThat(requests.single().accept).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
        assertThat(requests.single().body).contains("template")
    }

    @Test
    fun `postMonoStreaming sends json body and reads response`() {
        response = TestResponse(body = "\"created\"")
        val buffer = DefaultDataBufferFactory().wrap("""{"name":"template"}""".toByteArray())

        val result = client(serverBaseUrl()).postMonoStreaming(
            clazz = String::class.java,
            query = { uri -> uri.pathSegment("projects") },
            bodyStream = Flux.just(buffer),
        ).withAuth().block()

        assertThat(result).isEqualTo("\"created\"")
        assertThat(requests.single().method).isEqualTo("POST")
        assertThat(requests.single().contentType).contains(MediaType.APPLICATION_JSON_VALUE)
        assertThat(requests.single().body).isEqualTo("""{"name":"template"}""")
    }

    @Test
    fun `putMono sends bearer token and reads response`() {
        response = TestResponse(body = "\"updated\"")

        val result = client(serverBaseUrl()).putMono(
            clazz = String::class.java,
            query = { uri -> uri.pathSegment("projects", "123") },
            body = mapOf("name" to "template"),
        ).withAuth().block()

        assertThat(result).isEqualTo("\"updated\"")
        assertThat(requests.single().method).isEqualTo("PUT")
        assertThat(requests.single().path).isEqualTo("/api/v4/projects/123")
        assertThat(requests.single().authorization).isEqualTo("Bearer gitlab-access-token")
    }

    @Test
    fun `deleteMono sends bearer token and reads response`() {
        response = TestResponse(body = "\"deleted\"")

        val result = client(serverBaseUrl()).deleteMono(
            clazz = String::class.java,
            query = { uri -> uri.pathSegment("projects", "123") },
        ).withAuth().block()

        assertThat(result).isEqualTo("\"deleted\"")
        assertThat(requests.single().method).isEqualTo("DELETE")
        assertThat(requests.single().path).isEqualTo("/api/v4/projects/123")
        assertThat(requests.single().authorization).isEqualTo("Bearer gitlab-access-token")
    }

    @Test
    fun `getFlux maps GitLab error response`() {
        response = TestResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            contentType = MediaType.TEXT_PLAIN_VALUE,
            body = "bad request",
        )

        assertThatThrownBy {
            client(serverBaseUrl()).getFlux(clazz = String::class.java) { uri ->
                uri.pathSegment("projects")
            }.withAuth().collectList().block()
        }
            .isInstanceOf(GitLabHttpException::class.java)
            .hasMessage("GitLab HTTP 400: bad request")
    }

    @Test
    fun `getFlux maps unexpected redirect response`() {
        response = TestResponse(
            status = HttpStatus.FOUND.value(),
            contentType = MediaType.TEXT_PLAIN_VALUE,
            body = "login page",
        )

        assertThatThrownBy {
            client(serverBaseUrl()).getFlux(clazz = String::class.java) { uri ->
                uri.pathSegment("projects")
            }.withAuth().collectList().block()
        }
            .isInstanceOf(GitLabHttpException::class.java)
            .hasMessage("GitLab HTTP 302: Unexpected redirect. Body: login page")
    }

    private fun client(baseUrl: String = "https://gitlab.example.com") = GitLabClient(
        gitLabProperties = GitLabProperties(
            baseUrl = baseUrl,
            groupPath = "123",
            reviewersGroup = "reviewers",
            userInfoPath = "/oauth/userinfo",
        ),
        httpBuilder = WebClient.builder(),
        httpProperties = HttpProperties(timeout = 5),
    )

    private fun auth(accessToken: String = "gitlab-access-token") =
        UsernamePasswordAuthenticationToken("fabian", null, emptyList()).also {
            it.details = AuthSessionDetails(
                accessToken = accessToken,
                user = "fabian",
                email = "fabian@example.org",
                groups = listOf("reviewers"),
            )
        }

    private fun <T : Any> Mono<T>.withAuth(): Mono<T> =
        contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth()))

    private fun <T : Any> Flux<T>.withAuth(): Flux<T> =
        contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth()))

    private fun serverBaseUrl(): String = "http://localhost:${server.address.port}"

    private fun handle(exchange: HttpExchange) {
        val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
        requests += CapturedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.toString(),
            authorization = exchange.requestHeaders.getFirst(HttpHeaders.AUTHORIZATION),
            accept = exchange.requestHeaders.getFirst(HttpHeaders.ACCEPT),
            contentType = exchange.requestHeaders.getFirst(HttpHeaders.CONTENT_TYPE),
            body = requestBody,
        )

        val bytes = response.body.toByteArray()
        exchange.responseHeaders.add(HttpHeaders.CONTENT_TYPE, response.contentType)
        exchange.sendResponseHeaders(response.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private data class TestResponse(
        val status: Int = HttpStatus.OK.value(),
        val contentType: String = MediaType.APPLICATION_JSON_VALUE,
        val body: String = "\"ok\"",
    )

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val accept: String?,
        val contentType: String?,
        val body: String,
    )
}
