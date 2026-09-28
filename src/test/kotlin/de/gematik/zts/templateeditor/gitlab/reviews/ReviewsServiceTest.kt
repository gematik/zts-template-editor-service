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

package de.gematik.zts.templateeditor.gitlab.reviews

import de.gematik.zts.templateeditor.domain.auth.ReviewerAuthorization
import de.gematik.zts.templateeditor.domain.auth.UserDetailsSub
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateRequest
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateResponse
import de.gematik.zts.templateeditor.domain.reviews.CommentItem
import de.gematik.zts.templateeditor.domain.reviews.CommentsPayload
import de.gematik.zts.templateeditor.domain.reviews.ReviewsOverviewItem
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.gitlab.mapping.review.*
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

class ReviewsServiceTest {

    private val client = mockk<GitLabReviewsClient>()
    private val indexService = mockk<ResourceIndexService>()
    private val resolver = mockk<CommentFileResolver>()
    private val reviewerAuthorization = mockk<ReviewerAuthorization>()

    private val service = ReviewsService(
        client,
        indexService,
        resolver,
        reviewerAuthorization
    )

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `list maps merge request dto to overview items`() {
        val mr1 = mockk<GitLabMergeRequestDto> {
            every { iid } returns 11
            every { webUrl } returns "https://example.test/mr/11"
            every { projectId } returns "99"
        }

        every { client.listMergeRequests("opened", 1, 50) } returns Flux.just(mr1)

        val result = service.list("opened", 1, 50).collectList().block()!!

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            ReviewsOverviewItem(
                mr = MergeRequestRef(id = 11, webUrl = "https://example.test/mr/11"),
                projectId = "99"
            )
        )

        verify(exactly = 1) { client.listMergeRequests("opened", 1, 50) }
    }

    @Test
    fun `listComments filters system notes and notes without position or path`() {
        val repo = "p1"
        val mrId = "1"
        val branch = "b1"
        val version = "1.0.0"

        every { indexService.load(repo, branch, version) } returns Mono.just(ResourceIndex(mutableListOf()))

        val author = GitLabUser(
            name = "Alice",
            username = "alice"
        )

        val systemNote = mockk<GitLabNote> {
            every { system } returns true
        }

        val noPos = mockk<GitLabNote> {
            every { system } returns false
            every { position } returns null
        }

        val posNoPath = mockk<GitLabNote> {
            every { system } returns false
            val pos = mockk<GitLabPosition> {
                every { newPath } returns null
                every { oldPath } returns null
                every { newLine } returns 10
                every { oldLine } returns null
            }
            every { position } returns pos
        }

        val createdAt = OffsetDateTime.parse("2025-01-01T12:00:00Z")
        val okPos = mockk<GitLabPosition> {
            every { newPath } returns "1.0.0/input/templates/a.template.json"
            every { oldPath } returns null
            every { newLine } returns 123
            every { oldLine } returns null
        }
        val okNote = GitLabNote(
            system = false,
            position = okPos,
            id = 777L,
            body = "hi",
            author = author,
            createdAt = createdAt,
            resolved = null
        )

        val discussion = mockk<GitLabDiscussion> {
            every { id } returns "thread-1"
            every { resolved } returns true
            every { notes } returns listOf(systemNote, noPos, posNoPath, okNote)
        }

        every { client.getDiscussions(repo, mrId) } returns Flux.just(discussion)

        every {
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/input/templates/a.template.json",
                version = version,
                index = any()
            )
        } returns (CommitChange.Type.TEMPLATE to "a.template.json")

        val payload = service.listComments(repo, mrId, branch, version).block()!!

        assertThat(payload).isInstanceOf(CommentsPayload::class.java)
        assertThat(payload.comments).hasSize(1)

        val item = payload.comments.single()
        assertThat(item).isEqualTo(
            CommentItem(
                id = "777",
                fileName = "a.template.json",
                type = CommitChange.Type.TEMPLATE,
                line = 123,
                body = "hi",
                author = "Alice",
                createdAt = createdAt,
                resolved = true,
                threadId = "thread-1",
                isInline = true
            )
        )

        verify(exactly = 1) { indexService.load(repo, branch, version) }
        verify(exactly = 1) { client.getDiscussions(repo, mrId) }
        verify(exactly = 1) { resolver.resolveFromGitLabPath(any(), eq(version), any()) }
    }

    @Test
    fun `createComment throws when line is null`() {
        val req = CommentCreateRequest(
            repositoryId = "p1",
            branch = "b1",
            mrId = "1",
            version = "1.0.0",
            type = CommitChange.Type.TEMPLATE,
            fileName = "main.json",
            line = null,
            body = "hello"
        )

        assertThrows<IllegalArgumentException> {
            service.createComment(req).block()
        }

        verify { client wasNot Called }
        verify { indexService wasNot Called }
        verify { resolver wasNot Called }
    }

    @Test
    fun `createComment resolves path, uses diffRefs, calls createDiscussion and returns id`() {
        val req = CommentCreateRequest(
            repositoryId = "p1",
            branch = "b1",
            mrId = "1",
            version = "1.0.0",
            type = CommitChange.Type.TEMPLATE,
            fileName = "main.json",
            line = 42,
            body = "hello"
        )

        val idx = ResourceIndex(mutableListOf())
        every { indexService.load("p1", "b1", "1.0.0") } returns Mono.just(idx)

        every {
            resolver.resolveGitLabPath(
                fileName = "main.json",
                type = CommitChange.Type.TEMPLATE,
                version = "1.0.0",
                index = any()
            )
        } returns "1.0.0/input/templates/main.template.json"

        val mrDetails = GitLabMergeRequestDetails(
            diffRefs = GitLabMergeRequestDetails.DiffRefs(
                baseSha = "base",
                startSha = "start",
                headSha = "head"
            ),
            sha = "sha123"
        )

        every { client.getMergeRequestDetails("p1", "1") } returns Mono.just(mrDetails)

        val createReqSlot = slot<GitLabDiscussionCreateRequest>()
        val createResp = mockk<GitLabDiscussionCreateResponse> {
            every { id } returns "abc123"
        }

        every {
            client.createDiscussion("p1", "1", capture(createReqSlot))
        } returns Mono.just(createResp)

        val resp = service.createComment(req).block()!!

        assertThat(resp).isEqualTo(CommentCreateResponse("abc123"))

        val sent = createReqSlot.captured
        assertThat(sent.body).isEqualTo("hello")
        assertThat(sent.position.positionType).isEqualTo("text")
        assertThat(sent.position.baseSha).isEqualTo("base")
        assertThat(sent.position.startSha).isEqualTo("start")
        assertThat(sent.position.headSha).isEqualTo("head")
        assertThat(sent.position.newPath).isEqualTo("1.0.0/input/templates/main.template.json")
        assertThat(sent.position.newLine).isEqualTo(42)

        verify(exactly = 1) { indexService.load("p1", "b1", "1.0.0") }
        verify(exactly = 1) { client.getMergeRequestDetails("p1", "1") }
        verify(exactly = 1) { client.createDiscussion("p1", "1", any()) }
    }

    @Test
    fun `reply without resolve does not call resolveDiscussion`() {
        val noteResp = mockk<GitLabDiscussionNoteCreateResponse> {
            every { id } returns 10L
        }

        every { client.createNote("p1", "1", "t1", "ack") } returns Mono.just(noteResp)

        val resp = service.reply("p1", "1", "t1", "ack", resolved = false).block()!!

        assertThat(resp).isEqualTo(CommentCreateResponse("10"))
        verify(exactly = 1) { client.createNote("p1", "1", "t1", "ack") }
        verify(exactly = 0) { client.resolveDiscussion(any(), any(), any()) }
    }

    @Test
    fun `reply with resolve calls resolveDiscussion`() {
        val noteResp = mockk<GitLabDiscussionNoteCreateResponse> {
            every { id } returns 11L
        }

        every { client.createNote("p1", "1", "t1", "ack") } returns Mono.just(noteResp)
        every { client.resolveDiscussion("p1", "1", "t1") } returns Mono.just(mockk(relaxed = true))

        val resp = service.reply("p1", "1", "t1", "ack", resolved = true).block()!!

        assertThat(resp).isEqualTo(CommentCreateResponse("11"))
        verify(exactly = 1) { client.createNote("p1", "1", "t1", "ack") }
        verify(exactly = 1) { client.resolveDiscussion("p1", "1", "t1") }
    }

    @Test
    fun `approveAndMerge approves then merges using sha and returns message`() {
        every { client.approve("p1", "1") } returns Mono.just(mockk(relaxed = true))

        val details = mockk<GitLabMergeRequestDetails> {
            every { sha } returns "sha123"
        }
        every { client.getMergeRequestDetails("p1", "1") } returns Mono.just(details)
        every { client.merge("p1", "1", "sha123") } returns Mono.just(mockk(relaxed = true))
        every { reviewerAuthorization.ensureReviewerState(true) } returns Mono.empty()

        val resp = service.approveAndMerge("p1", "1").block()!!

        assertThat(resp).isEqualTo("approved + auto-merge triggered")

        verifyOrder {
            client.approve("p1", "1")
            client.getMergeRequestDetails("p1", "1")
            client.merge("p1", "1", "sha123")
        }
    }
}
