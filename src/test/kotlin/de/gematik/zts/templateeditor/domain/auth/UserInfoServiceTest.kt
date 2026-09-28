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

package de.gematik.zts.templateeditor.domain.auth

import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabUnauthorizedException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.charset.StandardCharsets

class UserInfoServiceTest {
    private val gitLabClient = mockk<GitLabClient>()
    private val gitLabProperties = GitLabProperties(
        baseUrl = "https://gitlab.example.com",
        groupPath = "123",
        reviewersGroup = "reviewers",
        userInfoPath = "/oauth/userinfo",
    )
    private val service = UserInfoService(gitLabClient, gitLabProperties)

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `userInfo uses oauth2-proxy forwarded headers and marks reviewer`() {
        val auth = authentication(
            AuthSessionDetails(
                accessToken = "access-token",
                user = "fabian",
                email = "fabian@example.org",
                groups = listOf("dev/publishers", "reviewers"),
            ),
        )

        val user = service.userInfo()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .block()

        assertThat(user).isNotNull
        assertThat(user!!.name).isEqualTo("fabian")
        assertThat(user.email).isEqualTo("fabian@example.org")
        assertThat(user.groups).containsExactly("dev/publishers", "reviewers")
        assertThat(user.isReviewer).isTrue()
        verify(exactly = 0) { gitLabClient.getFlux(any<Mono<URI>>(), UserDetailsSub::class.java, any<(UriBuilder) -> UriBuilder>()) }
    }

    @Test
    fun `userInfo falls back to GitLab userinfo when groups are missing`() {
        every {
            gitLabClient.getFlux(any<Mono<URI>>(), UserDetailsSub::class.java, any<(UriBuilder) -> UriBuilder>())
        } returns Flux.just(
            UserDetailsSub(
                name = "Fabian",
                email = "fabian@example.org",
                emailVerified = true,
                groups = listOf("dev/reviewers"),
            ),
        )

        every { gitLabClient.getUserInfoURI() } returns Mono.just(URI("https://gitlab.example.com/oauth/userinfo"))

        val auth = authentication(
            AuthSessionDetails(
                accessToken = "access-token",
                user = "fabian",
                email = "fabian@example.org",
                groups = emptyList(),
            ),
        )

        val user = service.userInfo()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .block()

        assertThat(user).isNotNull
        assertThat(user!!.groups).containsExactly("dev/reviewers")
        assertThat(user.isReviewer).isTrue()
    }

    @Test
    fun `userInfo maps GitLab userinfo errors`() {
        every {
            gitLabClient.getFlux(any<Mono<URI>>(), UserDetailsSub::class.java, any<(UriBuilder) -> UriBuilder>())
        } returns Flux.error(
            WebClientResponseException.create(
                401,
                "Unauthorized",
                HttpHeaders.EMPTY,
                ByteArray(0),
                StandardCharsets.UTF_8,
            ),
        )

        every { gitLabClient.getUserInfoURI() } returns Mono.just(URI("https://gitlab.example.com/oauth/userinfo"))

        val auth = authentication(
            AuthSessionDetails(
                accessToken = "access-token",
                user = "fabian",
                email = "fabian@example.org",
                groups = emptyList(),
            ),
        )

        assertThatThrownBy {
            service.userInfo()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block()
        }.isInstanceOf(GitLabUnauthorizedException::class.java)
    }

    @Test
    fun `userInfo treats missing GitLab groups as non reviewer`() {
        every {
            gitLabClient.getFlux(any<Mono<URI>>(), UserDetailsSub::class.java, any<(UriBuilder) -> UriBuilder>())
        } returns Flux.just(
            UserDetailsSub(
                name = "Fabian",
                email = "fabian@example.org",
                emailVerified = true,
            ),
        )

        every { gitLabClient.getUserInfoURI() } returns Mono.just(URI("https://gitlab.example.com/oauth/userinfo"))

        val auth = authentication(
            AuthSessionDetails(
                accessToken = "access-token",
                user = "fabian",
                email = "fabian@example.org",
                groups = emptyList(),
            ),
        )

        val user = service.userInfo()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .block()

        assertThat(user).isNotNull
        assertThat(user!!.groups).isEmpty()
        assertThat(user.isReviewer).isFalse()
    }

    @Test
    fun `userInfo fails without authentication details`() {
        assertThatThrownBy { service.userInfo().block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("no auth")
    }

    private fun authentication(details: AuthSessionDetails): UsernamePasswordAuthenticationToken =
        UsernamePasswordAuthenticationToken("principal", null, emptyList()).also { it.details = details }
}
