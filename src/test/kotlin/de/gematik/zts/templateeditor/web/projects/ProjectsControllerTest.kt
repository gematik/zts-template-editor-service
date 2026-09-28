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

package de.gematik.zts.templateeditor.web.projects

import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import de.gematik.zts.templateeditor.gitlab.ProjectsAdapter
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
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import java.time.OffsetDateTime

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProjectsControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var authTestSupport: AuthTestSupport
    @Autowired lateinit var projectsAdapter: ProjectsAdapter

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockProjectsAdapter(): ProjectsAdapter = mockk(relaxed = true)

        @Bean
        fun authTestSupport() = AuthTestSupport()
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }


    @Test
    fun `get projects without auth`() {
        every {
            projectsAdapter.list(anyNullable(), any(), any(), any())
        } returns Flux.empty()

        webTestClient.get()
            .uri("/projects")
            .exchange()
            .expectStatus().isUnauthorized

        verify { projectsAdapter wasNot Called }
    }

    @Test
    fun `get projects without params uses defaults`() {
        every { projectsAdapter.list(anyNullable(), any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/projects")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody().json("[]")

        val sortCaptor = slot<ProjectsController.SortKey>()
        verify { projectsAdapter.list(anyNullable(), capture(sortCaptor), eq(1), eq(50)) }
        assertThat(sortCaptor.captured).isEqualTo(ProjectsController.SortKey.LAST_CHANGE)
    }

    @Test
    fun `get projects with invalid sort returns 400`() {
        every { projectsAdapter.list(anyNullable(), any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/projects?sort=HelloWorld")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.message").isEqualTo("Ein oder mehrere Eingabewerte sind ungültig. Bitte prüfen Sie Ihre Angaben und korrigieren Sie die markierten Felder.")
            .jsonPath("$.details[0].field").isEqualTo("listProjects.sort")

        verify(exactly = 0) { projectsAdapter.list(anyNullable(), any(), any(), any()) }
    }

    @Test
    fun `get projects with unknown search params`() {
        every {
            projectsAdapter.list(anyNullable(), any(), any(), any())
        } returns Flux.empty()

        webTestClient.get()
            .uri("/projects")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody().json("[]")

        val sortCaptor = slot<ProjectsController.SortKey>()
        verify { projectsAdapter.list(anyNullable(), capture(sortCaptor), eq(1), eq(50)) }
        assertThat(sortCaptor.captured).isEqualTo(ProjectsController.SortKey.LAST_CHANGE)
    }

    @Test
    fun `get projects returns expected project data`() {
        val project1 = ProjectItem(
            projectId = 1L,
            title = "Gematik App",
            lastModified = OffsetDateTime.parse("2025-10-20T12:00:00Z"),
            description = "A Project"
        )
        val project2 = ProjectItem(
            projectId = 2L,
            title = "Backend Service",
            lastModified = OffsetDateTime.parse("2025-10-19T08:30:00Z"),
            description = "A Project"
        )

        every {
            projectsAdapter.list(anyNullable(), any(), any(), any())
        } returns Flux.just(project1, project2)

        webTestClient.get()
            .uri("/projects?sort=title")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].projectId").isEqualTo(1L)
            .jsonPath("$[0].title").isEqualTo("Gematik App")
            .jsonPath("$[1].title").isEqualTo("Backend Service")
            .jsonPath("$[1].projectId").isEqualTo(2L)

        verify {
            projectsAdapter.list(anyNullable(), ProjectsController.SortKey.TITLE, 1, 50)
        }
    }

}
