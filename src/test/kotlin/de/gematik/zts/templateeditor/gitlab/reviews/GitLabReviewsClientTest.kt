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

import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.mapping.review.*
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class GitLabReviewsClientTest {

    private val gl = mockk<GitLabClient>()
    private val props = GitLabProperties(
        baseUrl = "https://gitlab.example.com",
        groupPath = "my-group",
        reviewersGroup = "Reviewers",
    )

    private val client = GitLabReviewsClient(gl, props)

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `listMergeRequests builds expected uri and query params`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.getFlux(
                clazz = GitLabMergeRequestDto::class.java,
                query = capture(uriFnSlot)
            )
        } returns Flux.empty()

        client.listMergeRequests(state = "opened", page = 2, size = 50).subscribe()

        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo(
            "/groups/my-group/merge_requests" +
                    "?state=opened&scope=all&include_subgroups=true&page=2&per_page=50"
        )
    }

    @Test
    fun `getDiscussions builds expected uri`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.getFlux(
                clazz = GitLabDiscussion::class.java,
                query = capture(uriFnSlot)
            )
        } returns Flux.empty()

        client.getDiscussions(projectId = "123", mrId = "7").subscribe()


        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7/discussions?per_page=10000")
    }

    @Test
    fun `getMergeRequestDetails builds expected uri`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.getFlux(
                clazz = GitLabMergeRequestDetails::class.java,
                query = capture(uriFnSlot)
            )
        } returns Flux.empty()

        client.getMergeRequestDetails(projectId = "123", mrId = "7").subscribe()


        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7")
    }

    @Test
    fun `createDiscussion posts to expected uri and forwards request`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()
        val reqSlot = slot<GitLabDiscussionCreateRequest>()

        every {
            gl.postMono(
                GitLabDiscussionCreateResponse::class.java,
                capture(uriFnSlot),
                capture(reqSlot)
            )
        } returns Mono.empty()

        val req = GitLabDiscussionCreateRequest(
            body = "hi",
            position = GitLabDiscussionCreateRequest.Position(
                positionType = "text",
                baseSha = "base",
                startSha = "start",
                headSha = "head",
                newPath = "a/b.txt",
                newLine = 10
            )
        )

        client.createDiscussion("123", "7", req).subscribe()

        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7/discussions")
        assertThat(reqSlot.captured).isEqualTo(req)
    }

    @Test
    fun `createNote posts to expected uri and body contains text`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()
        val reqSlot = slot<GitLabDiscussionNoteCreateRequest>()

        every {
            gl.postMono(
                GitLabDiscussionNoteCreateResponse::class.java,
                capture(uriFnSlot),
                capture(reqSlot)
            )
        } returns Mono.empty()

        client.createNote(projectId = "123", mrId = "7", threadId = "t1", body = "ack").subscribe()


        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7/discussions/t1/notes")
        assertThat(reqSlot.captured.body).isEqualTo("ack")
    }

    @Test
    fun `resolveDiscussion puts to expected uri with resolved true`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()
        val bodySlot = slot<Map<String, Any>>()

        every {
            gl.putMono(
                GitLabDiscussion::class.java,
                capture(uriFnSlot),
                capture(bodySlot)
            )
        } returns Mono.empty()

        client.resolveDiscussion(projectId = "123", mrId = "7", threadId = "t1").subscribe()

        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7/discussions/t1")
        assertThat(bodySlot.captured).isEqualTo(mapOf("resolved" to true))
    }

    @Test
    fun `approve posts to expected uri`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.postMono(
                GitLabApproveResponse::class.java,
                capture(uriFnSlot),
                any()
            )
        } returns Mono.empty()

        client.approve(projectId = "123", mrId = "7").subscribe()


        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo("/projects/123/merge_requests/7/approve")
    }

    @Test
    fun `merge put to expected uri with sha and remove source branch`() {
        val uriFnSlot = slot<(UriBuilder) -> UriBuilder>()

        every {
            gl.putMono(
                GitLabMergeResponse::class.java,
                capture(uriFnSlot),
                any()
            )
        } returns Mono.empty()

        client.merge(projectId = "123", mrId = "7", sha = "abc").subscribe()

        val built = uriFnSlot.captured(UriComponentsBuilder.fromPath("/")).build().toString()

        assertThat(built).isEqualTo(
            "/projects/123/merge_requests/7/merge?sha=abc&should_remove_source_branch=true"
        )
    }
}
