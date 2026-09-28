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

import de.gematik.zts.templateeditor.domain.auth.ReviewerAuthorization
import de.gematik.zts.templateeditor.domain.versions.VersionItem
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabNotFoundException
import de.gematik.zts.templateeditor.gitlab.mapping.GitLabTreeItemDto
import de.gematik.zts.templateeditor.gitlab.mapping.version.GitLabCommitDto
import de.gematik.zts.templateeditor.gitlab.mapping.version.GitLabProjectDetailsDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.OffsetDateTime

class VersionsAdapterTest {
    private val gl = mockk<GitLabClient>()
    private val reviewerAuthorization = mockk<ReviewerAuthorization>()
    private val adapter = VersionsAdapter(gl, reviewerAuthorization)

    private fun treeItem(
        name: String,
        type: String = "tree",
        path: String? = name,
    ) = GitLabTreeItemDto(name = name.substringAfterLast('/'), type = type, path = path)

    @Nested
    inner class WithWorkspaceProvided {
        @Test
        fun `lists only semver top-level dirs and maps commit date`() {
            val projectId = "123"
            val workspace = "feature/x"
            val commitTs = "2025-10-10T12:00:00Z"

            every { gl.apiBase() } returns Mono.just(URI("http://example.com"))

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.fromIterable(
                    listOf(
                        treeItem("1.0.0"),
                        treeItem("1.0.0/extra"),
                        treeItem("abc"),
                    ),
                )

            every { gl.getFlux(clazz = GitLabCommitDto::class.java, query = any()) } returns
                Flux.just(
                    GitLabCommitDto(committedDate = commitTs),
                )

            val result: List<VersionItem> =
                adapter
                    .list(projectId, workspace)
                    .collectList()
                    .block()!!

            assertThat(result).hasSize(1)
            val v = result.first()
            assertThat(v.version).isEqualTo("1.0.0")
            assertThat(v.lastModified).isEqualTo(OffsetDateTime.parse(commitTs))

            verify(exactly = 0) { gl.getFlux(apiBase = any(), clazz = GitLabProjectDetailsDto::class.java, query = any()) }
        }
    }

    @Nested
    inner class WithoutWorkspace {
        @Test
        fun `resolves default branch and lists versions`() {
            val projectId = "999"
            val defaultBranch = "develop"
            val commitTs = "2025-09-01T08:30:00Z"

            every { gl.getFlux(clazz = GitLabProjectDetailsDto::class.java, query = any()) } returns
                Flux.just(
                    GitLabProjectDetailsDto(defaultBranch = defaultBranch),
                )

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.fromIterable(
                    listOf(
                        treeItem("1.2.3"),
                        treeItem("0.9.0"),
                        treeItem("README", type = "blob"),
                    ),
                )

            every { gl.getFlux(clazz = GitLabCommitDto::class.java, query = any()) } returns
                Flux.just(
                    GitLabCommitDto(committedDate = commitTs),
                )

            val items = adapter.list(projectId, null).collectList().block()!!

            assertThat(items).hasSize(2)
            assertThat(items.map { it.version }).containsExactlyInAnyOrder("1.2.3", "0.9.0")
            assertThat(items.all { it.lastModified == OffsetDateTime.parse(commitTs) }).isTrue
        }

        @Test
        fun `falls project details 404 oder leer - fallback main`() {
            val projectId = "1010"

            every { gl.getFlux(clazz = GitLabProjectDetailsDto::class.java, query = any()) } returns Flux.empty()
            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns Flux.empty()

            val items = adapter.list(projectId, null).collectList().block()!!
            assertThat(items).isEmpty()
        }
    }

    @Nested
    inner class EdgeCasesAndErrors {
        @Test
        fun `non semver and deeper levels are filtered`() {
            val projectId = "222"
            val workspace = "main"

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.fromIterable(
                    listOf(
                        treeItem("1.0.0/inner"),
                        treeItem("pkgOnly"),
                    ),
                )

            val items = adapter.list(projectId, workspace).collectList().block()!!
            assertThat(items).isEmpty()
        }

        @Test
        fun `lastModified falls keine commits - OffsetDateTime_MIN`() {
            val projectId = "333"
            val workspace = "main"

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.just(
                    treeItem("1.0.0"),
                )
            every { gl.getFlux(clazz = GitLabCommitDto::class.java, query = any()) } returns Flux.empty()

            val items = adapter.list(projectId, workspace).collectList().block()!!
            assertThat(items).hasSize(1)
            assertThat(items.first().version).isEqualTo("1.0.0")
            assertThat(items.first().lastModified).isEqualTo(OffsetDateTime.MIN)
        }

        @Test
        fun `404 vom Tree ergibt leere Liste statt Fehler`() {
            val projectId = "444"
            val workspace = "main"

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.error(GitLabNotFoundException("Not found"))

            val items = adapter.list(projectId, workspace).collectList().block()!!
            assertThat(items).isEmpty()
        }

        @Test
        fun `404 von Commits mapped auf OffsetDateTime_MIN`() {
            val projectId = "555"
            val workspace = "main"

            every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
                Flux.just(
                    treeItem("2.0.0"),
                )
            every { gl.getFlux(clazz = GitLabCommitDto::class.java, query = any()) } returns
                Flux.error(GitLabNotFoundException("Not found"))

            val items = adapter.list(projectId, workspace).collectList().block()!!
            assertThat(items).hasSize(1)
            assertThat(items.first().version).isEqualTo("2.0.0")
            assertThat(items.first().lastModified).isEqualTo(OffsetDateTime.MIN)
        }
    }

    private fun tree(
        name: String,
        path: String? = name,
    ) = GitLabTreeItemDto(name = name.substringAfterLast('/'), type = "tree", path = path)

    @Test
    fun `builds tree URI with ref, recursive, per_page (covers buildGitLabVersionsUri)`() {
        val projectId = "123"
        val branch = "dev"

        val treeUriSlot = slot<(UriBuilder) -> UriBuilder>()
        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = capture(treeUriSlot)) } returns Flux.empty()

        val result = adapter.list(projectId, branch).collectList().block()!!
        assertThat(result).isEmpty()

        val built = treeUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
        assertThat(built)
            .startsWith("/projects/123/repository/tree")
            .contains("ref=dev")
            .contains("recursive=true")
            .contains("per_page=2000")
    }

    @Test
    fun `builds commits URI with ref_name, path, per_page (covers lastModifiedFor)`() {
        val projectId = "123"
        val branch = "dev"

        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
            Flux.just(
                tree("1.2.3"),
            )

        val commitsUriSlot = slot<(UriBuilder) -> UriBuilder>()
        every { gl.getFlux(clazz = GitLabCommitDto::class.java, query = capture(commitsUriSlot)) } returns
            Flux.just(
                GitLabCommitDto(committedDate = "2025-10-10T12:00:00Z"),
            )

        val items: List<VersionItem> = adapter.list(projectId, branch).collectList().block()!!
        assertThat(items).hasSize(1)
        assertThat(items.first().version).isEqualTo("1.2.3")

        val built = commitsUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
        assertThat(built)
            .startsWith("/projects/123/repository/commits")
            .contains("ref_name=dev")
            .contains("path=1.2.3")
            .contains("per_page=1")
    }

    @Test
    fun `resolves default branch via project details (covers resolveBranch)`() {
        val projectId = "999"

        val detailsUriSlot = slot<(UriBuilder) -> UriBuilder>()
        every { gl.getFlux(clazz = GitLabProjectDetailsDto::class.java, query = capture(detailsUriSlot)) } returns
            Flux.just(
                GitLabProjectDetailsDto(defaultBranch = "release"),
            )

        val treeUriSlot = slot<(UriBuilder) -> UriBuilder>()
        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = capture(treeUriSlot)) } returns Flux.empty()

        val result = adapter.list(projectId, null).collectList().block()!!
        assertThat(result).isEmpty()

        val detailsBuilt = detailsUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
        assertThat(detailsBuilt).isEqualTo("/projects/999")

        val treeBuilt = treeUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
        assertThat(treeBuilt)
            .startsWith("/projects/999/repository/tree")
            .contains("ref=release")
    }

    @Test
    fun `project details 404 falls back to main (covers resolveBranch 404)`() {
        val projectId = "1001"

        every { gl.getFlux(clazz = GitLabProjectDetailsDto::class.java, query = any()) } returns
            Flux.error(GitLabHttpException(404, "Not Found"))

        val treeUriSlot = slot<(UriBuilder) -> UriBuilder>()
        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = capture(treeUriSlot)) } returns Flux.empty()

        val result = adapter.list(projectId, null).collectList().block()!!
        assertThat(result).isEmpty()

        val built = treeUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
        assertThat(built)
            .startsWith("/projects/1001/repository/tree")
            .contains("ref=main")
    }

    @Nested
    inner class DeleteVersionTests {
        @Test
        fun `deleteVersion mit workspace baut korrektes Commit und nennt Branch aus workspace`() {
            val projectId = "123"
            val workspace = "feature/x"
            val version = "1.2.3"

            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            val bodySlot = slot<Any>()

            every { reviewerAuthorization.ensureReviewerState(false) } returns Mono.empty()

            every {
                gl.postMono(
                    GitLabCommitDto::class.java,
                    capture(uriSlot),
                    capture(bodySlot),
                )
            } returns Mono.just(GitLabCommitDto(committedDate = "2025-01-01T00:00:00Z"))

            val delUriSlot = slot<(UriBuilder) -> UriBuilder>()
            every { gl.deleteMono(Void::class.java, capture(delUriSlot)) } returns Mono.empty()

            val result = adapter.deleteVersion(projectId, workspace, version).block()
            assertThat(result).isEqualTo(Unit)

            val uriBuilt = uriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
            assertThat(uriBuilt).isEqualTo("/projects/123/repository/commits")

            @Suppress("UNCHECKED_CAST")
            val body = bodySlot.captured as Map<String, Any>
            assertThat(body["branch"]).isEqualTo("feature/x")
            assertThat(body["commit_message"]).isEqualTo("Delete version $version")

            @Suppress("UNCHECKED_CAST")
            val actions = body["actions"] as List<Map<String, Any>>
            assertThat(actions).hasSize(1)
            val action = actions.first()
            assertThat(action["action"]).isEqualTo("delete")
            assertThat(action["file_path"]).isEqualTo(version)

            val delUriBuilt = delUriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
            assertThat(delUriBuilt).isEqualTo("/projects/123/repository/branches/feature%2Fx")
        }

        @Test
        fun `deleteVersion mit workspace null nutzt Branch aus project details oder main`() {
            val projectId = "999"
            val version = "3.4.5"

            every { gl.getFlux(clazz = GitLabProjectDetailsDto::class.java, query = any()) } returns
                Flux.just(
                    GitLabProjectDetailsDto(defaultBranch = "release"),
                )

            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            val bodySlot = slot<Any>()

            every { reviewerAuthorization.ensureReviewerState(false) } returns Mono.empty()

            every {
                gl.postMono(
                    GitLabCommitDto::class.java,
                    capture(uriSlot),
                    capture(bodySlot),
                )
            } returns Mono.just(GitLabCommitDto(committedDate = null))

            every { gl.deleteMono(Void::class.java, any()) } returns Mono.empty()

            val result = adapter.deleteVersion(projectId, null, version).block()
            assertThat(result).isEqualTo(Unit)

            val uriBuilt = uriSlot.captured(UriComponentsBuilder.fromPath("")).build().toString()
            assertThat(uriBuilt).isEqualTo("/projects/999/repository/commits")

            @Suppress("UNCHECKED_CAST")
            val body = bodySlot.captured as Map<String, Any>
            assertThat(body["branch"]).isEqualTo("release")
        }

        @Test
        fun `deleteVersion behandelt 404 von postMono als Erfolg`() {
            val projectId = "321"
            val workspace = "main"
            val version = "9.9.9"

            val uriSlot = slot<(UriBuilder) -> UriBuilder>()
            val bodySlot = slot<Any>()

            every {
                gl.postMono(
                    GitLabCommitDto::class.java,
                    capture(uriSlot),
                    capture(bodySlot),
                )
            } returns Mono.error(GitLabNotFoundException("Not Found"))
            every { reviewerAuthorization.ensureReviewerState(false) } returns Mono.empty()

            val result = adapter.deleteVersion(projectId, workspace, version).block()
            assertThat(result).isEqualTo(Unit)
        }

        @Test
        fun `deleteVersion loescht branch NICHT wenn branch main`() {
            val projectId = "123"
            val workspace = "main"
            val version = "1.2.3"

            every { reviewerAuthorization.ensureReviewerState(false) } returns Mono.empty()

            every { gl.postMono(GitLabCommitDto::class.java, any(), any()) } returns
                Mono.just(GitLabCommitDto(committedDate = null))

            val result = adapter.deleteVersion(projectId, workspace, version).block()
            assertThat(result).isEqualTo(Unit)

            verify(exactly = 0) { gl.deleteMono(Void::class.java, any()) }
        }

        @Test
        fun `deleteVersion loescht branch NICHT wenn branch dev`() {
            val projectId = "123"
            val workspace = "dev"
            val version = "1.2.3"

            every { reviewerAuthorization.ensureReviewerState(false) } returns Mono.empty()

            every { gl.postMono(GitLabCommitDto::class.java, any(), any()) } returns
                Mono.just(GitLabCommitDto(committedDate = null))

            val result = adapter.deleteVersion(projectId, workspace, version).block()
            assertThat(result).isEqualTo(Unit)

            verify(exactly = 0) { gl.deleteMono(Void::class.java, any()) }
        }
    }
}
