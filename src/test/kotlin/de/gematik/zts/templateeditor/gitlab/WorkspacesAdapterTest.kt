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
import de.gematik.zts.templateeditor.domain.workspaces.*
import de.gematik.zts.templateeditor.gitlab.commits.GitLabActionBuilder
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabServerErrorException
import de.gematik.zts.templateeditor.gitlab.fetcher.DetailFetcher
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.*
import de.gematik.zts.templateeditor.gitlab.validation.CommitContentProperties
import de.gematik.zts.templateeditor.gitlab.validation.FileTypeValidator
import io.mockk.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkspacesAdapterTest {

    private lateinit var gl: GitLabClient
    private lateinit var detailFetcher: DetailFetcher
    private lateinit var actionBuilder: GitLabActionBuilder
    private lateinit var adapter: WorkspacesAdapter
    private lateinit var commitContentProperties: CommitContentProperties
    private lateinit var fileTypeValidator: FileTypeValidator
    private lateinit var reviewerAuthorization: ReviewerAuthorization

    @BeforeEach
    fun setup() {
        gl = mockk()
        detailFetcher = mockk()
        actionBuilder = mockk()
        commitContentProperties = mockk()
        fileTypeValidator = FileTypeValidator()
        reviewerAuthorization = mockk()

        every { reviewerAuthorization.ensureReviewerHasOpenMrOrUnauthorized(any(), any()) } returns Mono.empty()
        every { commitContentProperties.tempDir } returns Path.of(System.getProperty("java.io.tmpdir"))

        adapter = WorkspacesAdapter(gl, detailFetcher, actionBuilder, commitContentProperties, fileTypeValidator, reviewerAuthorization)
    }

    /** Mocks [FilePart.transferTo] so it writes [bytes] to the path passed by the adapter. */
    private fun mockFilePartTransferTo(filePart: FilePart, bytes: ByteArray) {
        every { filePart.transferTo(any<Path>()) } answers {
            val path = firstArg<Path>()
            Files.write(path, bytes)
            Mono.empty()
        }
    }

    private fun commitReq(branch: String, createMR: Boolean = false) =
        CommitRequest(
            repositoryId = "123",
            branch = branch,
            version = "v1",
            message = "msg",
            changes = emptyList(),
            createMergeRequest = createMR
        )

    private fun createValidZip(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("dummy.txt")
            zos.putNextEntry(entry)
            zos.write("content".toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `ensureBranchExists - branch exists (no creation)`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(GitLabBranchDto("main"))

        val method = WorkspacesAdapter::class.java.getDeclaredMethod(
            "ensureBranchExists",
            String::class.java,
            String::class.java
        ).apply { isAccessible = true }

        val result = (method.invoke(adapter, "123", "main") as Mono<GitLabBranchDto>).block()!!

        assertThat(result.name).isEqualTo("main")
        verify(exactly = 0) { gl.postMono<GitLabBranchDto>(any(), any(), any()) }
    }

    @Test
    fun `ensureBranchExists - branch missing - created from default_branch`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.error(GitLabHttpException(404, "404"))
        every { gl.getFlux(clazz = GitLabProjectResp::class.java, query = any()) } returns Flux.just(GitLabProjectResp("main"))
        every { gl.postMono(GitLabBranchDto::class.java, any(), any()) } returns Mono.just(GitLabBranchDto("feature"))

        val method = WorkspacesAdapter::class.java.getDeclaredMethod(
            "ensureBranchExists",
            String::class.java,
            String::class.java
        ).apply { isAccessible = true }

        val result = (method.invoke(adapter, "123", "feature") as Mono<GitLabBranchDto>).block()!!

        assertThat(result.name).isEqualTo("feature")
    }

    @Test
    fun `commit - no MR creation`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query =  any()) } returns Flux.just(GitLabBranchDto("main"))
        every {
            actionBuilder.buildGitLabActions("123", "main", "v1", emptyList())
        } returns Mono.just(emptyList())
        every {
            gl.postMono(GitLabCommitResp::class.java, any(), any())
        } returns Mono.just(GitLabCommitResp("c123"))

        val res = adapter.commit(commitReq("main", createMR = false)).block()!!

        assertThat(res.commitId).isEqualTo("c123")
        assertThat(res.mergeRequest?.id).isEqualTo(-1)
        verify(exactly = 0) { gl.postMono(GitLabMergeRequestResp::class.java, any(), any()) }
    }

    @Test
    fun `commit - MR created`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(GitLabBranchDto("feature"))
        every {
            actionBuilder.buildGitLabActions("123", "feature", "v1",  emptyList())
        } returns Mono.just(emptyList())
        every {
            gl.postMono(GitLabCommitResp::class.java, any(), any())
        } returns Mono.just(GitLabCommitResp("c999"))
        every { gl.getFlux(clazz = GitLabProjectResp::class.java, query = any()) } returns Flux.just(GitLabProjectResp("main"))
        every {
            gl.postMono(GitLabMergeRequestResp::class.java, any(), any())
        } returns Mono.just(GitLabMergeRequestResp(77, "http://mr"))

        val res = adapter.commit(commitReq("feature", createMR = true)).block()!!

        assertThat(res.commitId).isEqualTo("c999")
        assertThat(res.mergeRequest!!.id).isEqualTo(77)
        assertThat(res.mergeRequest.webUrl).isEqualTo("http://mr")
    }

    @Test
    fun `commit - MR suppressed when source == target branch`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(GitLabBranchDto("main"))
        every {
            actionBuilder.buildGitLabActions("123", "main", "v1",  emptyList())
        } returns Mono.just(emptyList())
        every {
            gl.postMono(GitLabCommitResp::class.java, any(), any())
        } returns Mono.just(GitLabCommitResp("c111"))
        every { gl.getFlux(clazz = GitLabProjectResp::class.java, query = any()) } returns Flux.just(GitLabProjectResp("main"))

        val res = adapter.commit(commitReq("main", createMR = true)).block()!!

        assertThat(res.mergeRequest!!.id).isEqualTo(-1)
    }

    @Test
    fun `listBranches - null commit date mapped to null lastModified`() {
        val branchDto = GitLabBranchDto("dev")
        val commitMini = GitLabCommitMiniDto(null, "me")
        val gitLabMergeRequestDto = GitLabMergeRequestDto(1, "url", "dev", "main", "opened")

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(branchDto)
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.just(gitLabMergeRequestDto)
        every { gl.getFlux(clazz = GitLabCommitMiniDto::class.java, query = any()) } returns Flux.just(commitMini)

        val list = adapter.listBranches("123").collectList().block()!!

        assertThat(list).hasSize(1)
        assertThat(list[0].branch).isEqualTo("dev")
        assertThat(list[0].author).isEqualTo("me")
        assertThat(list[0].lastModified).isNull()
    }

    @Test
    fun `listBranches - builds correct URIs for branches and commits`() {
        val branchDto = GitLabBranchDto("dev")
        val gitLabMergeRequestDto = GitLabMergeRequestDto(1, "url", "dev", "main", "opened")

        val branchesUriSlot = slot<(UriBuilder) -> UriBuilder>()
        val commitsUriSlot = slot<(UriBuilder) -> UriBuilder>()

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = capture(branchesUriSlot)) } returns
                Flux.just(branchDto)
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.just(gitLabMergeRequestDto)
        every { gl.getFlux(clazz = GitLabCommitMiniDto::class.java, query = capture(commitsUriSlot)) } returns
                Flux.just(GitLabCommitMiniDto("2024-01-01T00:00:00Z", "me"))

        adapter.listBranches("123").collectList().block()

        val branchesComponents = branchesUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(branchesComponents.path)
            .isEqualTo("/projects/123/repository/branches")
        assertThat(branchesComponents.query)
            .isEqualTo("per_page=100")

        val commitsComponents = commitsUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(commitsComponents.path)
            .isEqualTo("/projects/123/repository/commits")
        assertThat(commitsComponents.query).isNotNull
        assertThat(commitsComponents.query!!)
            .contains("ref_name=dev")
            .contains("per_page=1")
    }

    @Test
    fun `ensureBranchExists - builds correct URI for branch lookup`() {
        val branchUriSlot = slot<(UriBuilder) -> UriBuilder>()

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = capture(branchUriSlot)) } returns
                Flux.just(GitLabBranchDto("feature"))

        val method = WorkspacesAdapter::class.java
            .getDeclaredMethod("ensureBranchExists", String::class.java, String::class.java)
            .apply { isAccessible = true }

        (method.invoke(adapter, "123", "feature") as Mono<GitLabBranchDto>).block()

        val components = branchUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(components.path)
            .isEqualTo("/projects/123/repository/branches/feature")
        assertThat(components.query).isNull()
    }

    @Test
    fun `createBranch - builds correct URI for branch creation`() {
        val postUriSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.postMono(GitLabBranchDto::class.java, capture(postUriSlot), any())
        } returns Mono.just(GitLabBranchDto("feature"))

        val method = WorkspacesAdapter::class.java
            .getDeclaredMethod(
                "createBranch",
                String::class.java, String::class.java, GitLabProjectResp::class.java
            )
            .apply { isAccessible = true }

        (method.invoke(adapter, "123", "feature", GitLabProjectResp("main")) as Mono<GitLabBranchDto>).block()

        val components = postUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(components.path)
            .isEqualTo("/projects/123/repository/branches")
        assertThat(components.query).isNotNull
        assertThat(components.query!!)
            .contains("branch=feature")
            .contains("ref=main")
    }

    @Test
    fun `commit - builds correct URI for commit endpoint`() {
        val branchUriSlot = slot<(UriBuilder) -> UriBuilder>()
        val commitUriSlot = slot<(UriBuilder) -> UriBuilder>()

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = capture(branchUriSlot)) } returns
                Flux.just(GitLabBranchDto("main"))
        every { actionBuilder.buildGitLabActions("123", "main",  "v1", emptyList()) } returns
                Mono.just(emptyList())

        every {
            gl.postMono(GitLabCommitResp::class.java, capture(commitUriSlot), any())
        } returns Mono.just(GitLabCommitResp("c123"))

        val res = adapter.commit(commitReq("main", createMR = false)).block()!!
        assertThat(res.commitId).isEqualTo("c123")

        val components = commitUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(components.path)
            .isEqualTo("/projects/123/repository/commits")
        assertThat(components.query).isNull()
    }

    @Test
    fun `openByMr - builds correct URI for merge request lookup`() {
        val mrUriSlot = slot<(UriBuilder) -> UriBuilder>()

        val mr = GitLabMergeRequestDto(5, "http://x", "feat-123", "main", "opened")

        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = capture(mrUriSlot)) } returns
                Flux.just(mr)

        every { detailFetcher.details("123", "feat-123",  "v1") } returns
                Mono.just(WorkspaceDetails(emptyList(), emptyList(), "", "", "", "", "", "", "", "", "", emptyList(), null))

        adapter.openByMr("123", 5,  "v1").block()

        val components = mrUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(components.path)
            .isEqualTo("/projects/123/merge_requests/5")
        assertThat(components.query).isNull()
    }

    @Test
    fun `mapToMergeRequestResponse - builds correct URI for MR creation`() {
        val mrPostUriSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.postMono(GitLabMergeRequestResp::class.java, capture(mrPostUriSlot), any())
        } returns Mono.just(GitLabMergeRequestResp(77, "http://mr"))

        val method = WorkspacesAdapter::class.java
            .getDeclaredMethod(
                "mapToMergeRequestResponse",
                GitLabProjectResp::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            .apply { isAccessible = true }

        val projectResp = GitLabProjectResp("main")
        val title = "msg"

        val mono = method.invoke(adapter, projectResp, "feature", "123", title) as Mono<MergeRequestRef?>
        mono.block()

        val components = mrPostUriSlot.captured
            .invoke(UriComponentsBuilder.fromPath(""))
            .build(true)

        assertThat(components.path)
            .isEqualTo("/projects/123/merge_requests")
        assertThat(components.query).isNotNull
        assertThat(components.query!!)
            .contains("source_branch=feature")
            .contains("target_branch=main")
            .contains("title=msg")
            .contains("remove_source_branch=false")
    }

    @Test
    fun `commit - MR creation 404 returns default MR`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("feature"))
        every { actionBuilder.buildGitLabActions("123", "feature",  "v1", emptyList()) } returns
                Mono.just(emptyList())
        every { gl.postMono(GitLabCommitResp::class.java, any(), any()) } returns
                Mono.just(GitLabCommitResp("c404"))
        every { gl.getFlux(clazz = GitLabProjectResp::class.java, query = any()) } returns
                Flux.error(GitLabHttpException(404, "404"))

        val res = adapter.commit(commitReq("feature", createMR = true)).block()!!

        assertThat(res.commitId).isEqualTo("c404")
        assertThat(res.mergeRequest!!.id).isEqualTo(-1)
        assertThat(res.mergeRequest.webUrl).isEmpty()
        verify(exactly = 0) { gl.postMono(GitLabMergeRequestResp::class.java, any(), any()) }
    }

    @Test
    fun `commit - MR creation non 404 409 error bubbles up`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("feature"))
        every { actionBuilder.buildGitLabActions("123", "feature", "v1", emptyList()) } returns
                Mono.just(emptyList())
        every { gl.postMono(GitLabCommitResp::class.java, any(), any()) } returns
                Mono.just(GitLabCommitResp("c500"))
        every { gl.getFlux(clazz = GitLabProjectResp::class.java, query = any()) } returns
                Flux.error(GitLabHttpException(500, "500"))

        val mono = adapter.commit(commitReq("feature", createMR = true))

        assertThatThrownBy { mono.block() }
            .isInstanceOf(GitLabHttpException::class.java)
    }

    @Test
    fun `commit - commit call failure bubbles up`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("main"))
        every { actionBuilder.buildGitLabActions("123", "main",  "v1", emptyList()) } returns
                Mono.just(emptyList())
        every { gl.postMono(GitLabCommitResp::class.java, any(), any()) } returns
                Mono.error(GitLabHttpException(500, "commit failed"))

        val mono = adapter.commit(commitReq("main", createMR = false))

        assertThatThrownBy { mono.block() }
            .isInstanceOf(GitLabServerErrorException::class.java)

        verify(exactly = 0) { gl.postMono(GitLabMergeRequestResp::class.java, any(), any()) }
    }

    @Test
    fun `openByMr - propagates GitLabHttpException`() {
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns
                Flux.error(GitLabHttpException(500, "boom"))

        val mono = adapter.openByMr("123", 5,  "v1")

        assertThatThrownBy { mono.block() }
            .isInstanceOf(GitLabServerErrorException::class.java)
    }

    @Test
    fun `openByMr - fetches merge request and details`() {
        val mr = GitLabMergeRequestDto(5, "http://x", "feat-123", "main", "opened")

        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.just(mr)
        every {
            detailFetcher.details("123", "feat-123",  "v1")
        } returns Mono.just(
            WorkspaceDetails(emptyList(), emptyList(), "", "", "", "", "", "", "", "", "", emptyList(), null)
        )

        val res = adapter.openByMr("123", 5,  "v1").block()!!
        assertThat(res.templateJsons).isEmpty()
        assertThat(res.templateMds).isEmpty()
    }

    @Test
    fun `listBranches - maps commit information without merge request`() {
        val branchDto = GitLabBranchDto(name = "dev", default = false, protected = false)
        val commitMini = GitLabCommitMiniDto("2024-01-01T00:00:00Z", "me")

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(branchDto)
        every { gl.getFlux(clazz = GitLabCommitMiniDto::class.java, query = any()) } returns Flux.just(commitMini)
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.empty()

        val list = adapter.listBranches("123").collectList().block()!!

        assertThat(list).hasSize(1)
        val item = list[0]
        assertThat(item.branch).isEqualTo("dev")
        assertThat(item.author).isEqualTo("me")
        assertThat(item.lastModified).isEqualTo(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        assertThat(item.default).isFalse()
        assertThat(item.protected).isFalse()
        assertThat(item.mergeRequestRef).isNull()
    }

    @Test
    fun `listBranches - maps merge request information`() {
        val branchDto = GitLabBranchDto(name = "dev", default = true, protected = true)
        val commitMini = GitLabCommitMiniDto("2024-01-01T00:00:00Z", "me")
        val mrDto = GitLabMergeRequestDto(42, "http://mr-url", "dev", "main", "opened")

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.just(branchDto)
        every { gl.getFlux(clazz = GitLabCommitMiniDto::class.java, query = any()) } returns Flux.just(commitMini)
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.just(mrDto)

        val list = adapter.listBranches("123").collectList().block()!!

        assertThat(list).hasSize(1)
        val item = list[0]
        assertThat(item.branch).isEqualTo("dev")
        assertThat(item.author).isEqualTo("me")
        assertThat(item.lastModified).isEqualTo(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        assertThat(item.default).isTrue()
        assertThat(item.protected).isTrue()
        assertThat(item.mergeRequestRef).isNotNull
        assertThat(item.mergeRequestRef!!.id).isEqualTo(42)
        assertThat(item.mergeRequestRef.webUrl).isEqualTo("http://mr-url")
    }

    @Test
    fun `listBranches - 404 suppressed`() {
        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns Flux.error(GitLabHttpException(404, "404"))
        val list = adapter.listBranches("123").collectList().block()!!
        assertThat(list).isEmpty()
    }

    @Test
    fun `commitFile builds base64 CommitRequest and forwards to commit`() {
        every { commitContentProperties.maxLength } returns 10_000L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = createValidZip()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        // Capture and eagerly collect the streamed body DURING the mock call,
        // because Mono.usingWhen deletes the temp file after completion.
        val capturedBodyBytes = mutableListOf<ByteArray>()

        every {
            gl.postMonoStreaming(
                GitLabCommitResp::class.java,
                any(),
                any<Flux<DataBuffer>>()
            )
        } answers {
            val bodyStream = thirdArg<Flux<DataBuffer>>()
            // Eagerly consume the stream while the temp file still exists
            val collected = DataBufferUtils.join(bodyStream)
                .map { buf ->
                    val bytes = ByteArray(buf.readableByteCount())
                    buf.read(bytes)
                    DataBufferUtils.release(buf)
                    bytes
                }.block()!!
            capturedBodyBytes.add(collected)
            Mono.just(GitLabCommitResp("c1", "c1short"))
        }

        val res = adapterSpy.commitFile(
            repositoryId = "123",
            branch = "dev",
            version = "v1",
            message = "msg",
            action = "update",
            file = filePart,
            fileName = "input.zip"
        ).block()!!

        assertThat(res.commitId).isEqualTo("c1")
        assertThat(res.head).isEqualTo("dev")

        // Verify the collected JSON body
        assertThat(capturedBodyBytes).hasSize(1)
        val json = ObjectMapper().readTree(String(capturedBodyBytes[0], Charsets.UTF_8))
        assertThat(json["branch"].asString()).isEqualTo("dev")
        assertThat(json["commit_message"].asString()).isEqualTo("msg")

        val action = json["actions"][0]
        assertThat(action["action"].asString()).isEqualTo("update")
        assertThat(action["file_path"].asString()).isEqualTo("v1/input/input.zip")
        assertThat(action["encoding"].asString()).isEqualTo("base64")

        val expectedBase64 = Base64.getEncoder().encodeToString(fileBytes)
        assertThat(action["content"].asString()).isEqualTo(expectedBase64)

        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
        verify(exactly = 0) { adapterSpy.commit(any()) }
    }

    @Test
    fun `commitFile rejects invalid action`() {
        every { commitContentProperties.maxLength } returns 10_000L
        val adapterSpy = spyk(adapter)

        val filePart = mockk<FilePart>(relaxed = true)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "delete",
                file = filePart,
                fileName = "input.zip"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)

        verify(exactly = 0) { adapterSpy.commit(any()) }
    }

    @Test
    fun `commitFile rejects illegal fileName`() {
        every { commitContentProperties.maxLength } returns 10_000L
        val adapterSpy = spyk(adapter)

        val filePart = mockk<FilePart>(relaxed = true)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "../evil.zip"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("fileName contains illegal characters")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 0) { filePart.transferTo(any<Path>()) }
    }

    @Test
    fun `commitFile rejects file too large`() {
        every { commitContentProperties.maxLength } returns 5L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = "123456".toByteArray()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "input.zip"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("File too large")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
    }

    @Test
    fun `commitFile rejectes file not valid filetype`() {
        every { commitContentProperties.maxLength } returns 10_000L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = "123456".toByteArray()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "file.some"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid data type: '.zip', '.xml', and '.json' are permitted.")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
    }

    @Test
    fun `commitFile rejectes file not valid zip`() {
        every { commitContentProperties.maxLength } returns 10_000L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = "123456".toByteArray()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "file.zip"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("File is not a valid ZIP file")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
    }

    @Test
    fun `commitFile rejectes file not valid json`() {
        every { commitContentProperties.maxLength } returns 10_000L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = """{"resourceType":"Patient"}""".toByteArray()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "file.json"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("The file is not valid FHIR JSON. JSON must have the correct syntax and the ResourceType must be ValueSet, ConceptMap, or CodeSystem.")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
    }

    @Test
    fun `commitFile rejectes file not valid xml`() {
        every { commitContentProperties.maxLength } returns 10_000L

        every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                Flux.just(GitLabBranchDto("dev"))

        val adapterSpy = spyk(adapter)

        val fileBytes = "123456".toByteArray()

        val filePart = mockk<FilePart>()
        mockFilePartTransferTo(filePart, fileBytes)

        assertThatThrownBy {
            adapterSpy.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "v1",
                message = "msg",
                action = "update",
                file = filePart,
                fileName = "file.xml"
            ).block()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("The file is not valid FHIR XML. XML must have the correct syntax and the root tag must be \"ValueSet,\" \"ConceptMap,\" or \"CodeSystem.\"")

        verify(exactly = 0) { adapterSpy.commit(any()) }
        verify(exactly = 1) { filePart.transferTo(any<Path>()) }
    }
}
