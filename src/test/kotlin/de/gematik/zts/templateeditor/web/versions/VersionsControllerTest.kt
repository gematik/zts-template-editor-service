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

package de.gematik.zts.templateeditor.web.versions

import de.gematik.zts.templateeditor.domain.versions.VersionItem
import de.gematik.zts.templateeditor.gitlab.VersionsAdapter
import de.gematik.zts.templateeditor.web.AuthTestSupport
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class VersionsControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var versionsAdapter: VersionsAdapter
    @Autowired lateinit var authTestSupport: AuthTestSupport

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockVersionsAdapter(): VersionsAdapter = mockk(relaxed = true)

        @Bean
        fun authTestSupport() = AuthTestSupport()
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `get versions without auth`() {
        every { versionsAdapter.list(any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/projects/1/versions")
            .exchange()
            .expectStatus().isUnauthorized

        verify { versionsAdapter wasNot Called }
    }

    @Test
    fun `defaults applied and called with workspace null`() {
        every { versionsAdapter.list(any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/projects/123/versions")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody().json("[]")

        val projectCaptor = slot<String>()

        verify {
            versionsAdapter.list(
                projectId = capture(projectCaptor),
                workspace = isNull()
            )
        }
        assertThat(projectCaptor.captured).isEqualTo("123")
    }

    @Test
    fun `workspace passthrough`() {
        every { versionsAdapter.list(any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/projects/42/versions?workspace=module")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk

        verify { versionsAdapter.list("42", "module") }
    }

    @Test
    fun `returns flux from adapter (smoke length)`() {
        val v1 = versionItem(1)
        val v2 = versionItem(2)
        every { versionsAdapter.list(any(), any()) } returns Flux.just(v1, v2)

        webTestClient.get()
            .uri("/projects/77/versions")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)

        verify { versionsAdapter.list("77", isNull()) }
    }

    @Test
    fun `delete version without auth`() {
        webTestClient.delete()
            .uri("/projects/1/versions/1.2.3")
            .exchange()
            .expectStatus().isUnauthorized

        verify { versionsAdapter wasNot Called }
    }

    @Test
    fun `delete version with auth returns 204 and calls adapter`() {
        every { versionsAdapter.deleteVersion(any(), any(), any()) } returns Mono.just(Unit)

        webTestClient.delete()
            .uri("/projects/123/versions/1.2.3")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isNoContent

        verify {
            versionsAdapter.deleteVersion(
                "123",
                null,
                "1.2.3"
            )
        }
    }

    @Test
    fun `delete version workspace passthrough`() {
        every { versionsAdapter.deleteVersion(any(), any(), any()) } returns Mono.just(Unit)

        webTestClient.delete()
            .uri("/projects/42/versions/2.0.0?workspace=module")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isNoContent

        verify {
            versionsAdapter.deleteVersion(
                "42",
                "module",
                "2.0.0"
            )
        }
    }

    private fun versionItem(n: Int) = VersionItem(
        version = "1.0.$n",
        lastModified = OffsetDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)
    )
}
