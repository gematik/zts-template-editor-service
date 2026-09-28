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

package de.gematik.zts.templateeditor.web.auth

import de.gematik.zts.templateeditor.domain.auth.UserDetailsSub
import de.gematik.zts.templateeditor.domain.auth.UserInfoService
import de.gematik.zts.templateeditor.web.AuthTestSupport
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var authTestSupport: AuthTestSupport
    @Autowired lateinit var userInfoService: UserInfoService

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockUserInfoService(): UserInfoService = mockk(relaxed = true)

        @Bean
        fun authTestSupport() = AuthTestSupport()
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `GET userinfo without auth is unauthorized`() {
        webTestClient.get()
            .uri("/auth/userinfo")
            .exchange()
            .expectStatus().isUnauthorized

        verify { userInfoService wasNot Called }
    }

    @Test
    fun `GET userinfo delegates to UserInfoService and returns reviewer mapping`() {
        every { userInfoService.userInfo() } returns Mono.just(
            UserDetailsSub(
                name = "Fabian Müller",
                email = "fabian@example.org",
                emailVerified = true,
                groups = listOf("dev/reviewers"),
                isReviewer = true,
            ),
        )

        webTestClient.get()
            .uri("/auth/userinfo")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Fabian Müller")
            .jsonPath("$.email").isEqualTo("fabian@example.org")
            .jsonPath("$.email_verified").isEqualTo(true)
            .jsonPath("$.groups[0]").isEqualTo("dev/reviewers")
            .jsonPath("$.is_reviewer").isEqualTo(true)

        verify(exactly = 1) { userInfoService.userInfo() }
    }
}
