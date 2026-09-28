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
import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import de.gematik.zts.templateeditor.gitlab.mapping.project.GitLabProjectDto
import de.gematik.zts.templateeditor.web.projects.ProjectsController.SortKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.function.Consumer

class ProjectsAdapterTest {
    private val properties = GitLabProperties(baseUrl = "https://gitlab.example.com", groupPath = "my/group", reviewersGroup = "Reviewers")

    private fun dto(
        id: Long,
        name: String,
        path: String,
        lastActivity: String = OffsetDateTime.now().toString(),
        nameWithNs: String? = null,
    ) = GitLabProjectDto(
        id = id,
        name = name,
        nameWithNamespace = nameWithNs,
        path = path,
        lastActivityAt = lastActivity,
        defaultBranch = "main",
        description = "A project",
    )

    @Nested
    inner class UriBuilding {
        @Test
        fun `builds uri for TITLE sort without search`() {
            val gl = mockk<GitLabClient>()
            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = capture(uriSlot)) } returns Flux.empty()

            val adapter = ProjectsAdapter(gl, properties)

            adapter
                .list(search = null, sort = SortKey.TITLE, page = 2, size = 20)
                .collectList()
                .block()

            val built =
                uriSlot
                    .captured(UriComponentsBuilder.fromPath(""))
                    .build()
                    .toString()

            assertThat(built)
                .startsWith("/groups/my%2Fgroup/projects")
                .contains("include_subgroups=true")
                .contains("simple=true")
                .contains("order_by=name")
                .contains("sort=desc")
                .contains("per_page=20")
                .contains("page=2")
                .doesNotContain("search=")
        }

        @Test
        fun `builds uri for LAST_CHANGE sort with search`() {
            val gl = mockk<GitLabClient>()
            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = capture(uriSlot)) } returns Flux.empty()

            val adapter = ProjectsAdapter(gl, properties)

            adapter
                .list(search = "fhir", sort = SortKey.LAST_CHANGE, page = 1, size = 50)
                .collectList()
                .block()

            val built =
                uriSlot
                    .captured(UriComponentsBuilder.fromPath(""))
                    .build()
                    .toString()

            assertThat(built)
                .contains("order_by=last_activity_at")
                .contains("search=fhir")
                .contains("per_page=50")
                .contains("page=1")
        }

        @Test
        fun `blank search not added`() {
            val gl = mockk<GitLabClient>()
            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = capture(uriSlot)) } returns Flux.empty()

            val adapter = ProjectsAdapter(gl, properties)

            adapter
                .list(search = "   ", sort = SortKey.TITLE, page = 1, size = 10)
                .collectList()
                .block()

            val built =
                uriSlot
                    .captured(UriComponentsBuilder.fromPath(""))
                    .build()
                    .toString()

            assertThat(built).doesNotContain("search=")
        }
    }

    @Nested
    inner class FilteringAndMapping {
        @Test
        fun `filters only names with 1 or more dots`() {
            val gl = mockk<GitLabClient>()
            val dtos =
                listOf(
                    dto(1, "a.b.c", "proj-a"),
                    dto(2, "a.b.c.d", "proj-b"),
                    dto(3, "a.b", "proj-c"),
                    dto(4, "a.b.c.d.e", "proj-d"),
                    dto(5, "a", "proj-e"),
                )
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = any()) } returns Flux.fromIterable(dtos)

            val adapter = ProjectsAdapter(gl, properties)

            val result =
                adapter
                    .list(null, SortKey.TITLE, 1, 10)
                    .collectList()
                    .block()!!

            assertThat(result).hasSize(4)
        }

        @Test
        fun `filters ctl paths`() {
            val gl = mockk<GitLabClient>()
            val dtos =
                listOf(
                    dto(1, "a.b.c", "ctl-foo"),
                    dto(2, "a.b.c", "CTL-bar"),
                    dto(3, "a.b.c", "normal-proj"),
                )
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = any()) } returns Flux.fromIterable(dtos)

            val adapter = ProjectsAdapter(gl, properties)

            val result =
                adapter
                    .list(null, SortKey.TITLE, 1, 10)
                    .collectList()
                    .block()!!

            assertThat(result)
                .hasSize(1)
        }

        @Test
        fun `maps dto to ProjectItem`() {
            val ts = OffsetDateTime.now().toString()
            val gl = mockk<GitLabClient>()
            val dtos =
                listOf(
                    dto(
                        id = 42,
                        name = "a.b.c",
                        path = "some/proj",
                        lastActivity = ts,
                        nameWithNs = "Fancy / a.b.c",
                    ),
                )
            every { gl.getFlux(clazz = GitLabProjectDto::class.java, query = any()) } returns Flux.fromIterable(dtos)

            val adapter = ProjectsAdapter(gl, properties)

            val result =
                adapter
                    .list(null, SortKey.TITLE, 1, 10)
                    .collectList()
                    .block()!!

            assertThat(result)
                .hasSize(1)
                .first()
                .satisfies(
                    Consumer { pi ->
                        assertThat(pi.projectId).isEqualTo(42)
                        assertThat(pi.title).isEqualTo("a.b.c")
                        assertThat(pi.lastModified).isEqualTo(OffsetDateTime.parse(ts))
                    },
                )
        }
    }
}
