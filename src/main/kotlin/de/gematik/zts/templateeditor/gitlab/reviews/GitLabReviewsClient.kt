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
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabApproveResponse
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussion
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussionCreateRequest
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussionCreateResponse
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussionNoteCreateRequest
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussionNoteCreateResponse
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabMergeRequestDetails
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabMergeResponse
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class GitLabReviewsClient(
    private val gl: GitLabClient,
    private val props: GitLabProperties,
) {
    fun listMergeRequests(
        state: String,
        page: Int,
        size: Int,
    ): Flux<GitLabMergeRequestDto> =
        gl.getFlux(clazz = GitLabMergeRequestDto::class.java) { uri ->
            uri
                .pathSegment("groups", props.groupPath, "merge_requests")
                .queryParam("state", state)
                .queryParam("scope", "all")
                .queryParam("include_subgroups", true)
                .queryParam("page", page)
                .queryParam("per_page", size)
        }

    fun getDiscussions(
        projectId: String,
        mrId: String,
    ): Flux<GitLabDiscussion> =
        gl.getFlux(clazz = GitLabDiscussion::class.java) { uri ->
            uri
                .pathSegment("projects", projectId, "merge_requests", mrId, "discussions")
                .queryParam("per_page", 10000)
        }

    fun getMergeRequestDetails(
        projectId: String,
        mrId: String,
    ): Mono<GitLabMergeRequestDetails> =
        gl
            .getFlux(clazz = GitLabMergeRequestDetails::class.java) { uri ->
                uri.pathSegment("projects", projectId, "merge_requests", mrId)
            }.next()

    fun createDiscussion(
        projectId: String,
        mrId: String,
        req: GitLabDiscussionCreateRequest,
    ): Mono<GitLabDiscussionCreateResponse> =
        gl.postMono(
            GitLabDiscussionCreateResponse::class.java,
            { uri ->
                uri.pathSegment("projects", projectId, "merge_requests", mrId, "discussions")
            },
            req,
        )

    fun createNote(
        projectId: String,
        mrId: String,
        threadId: String,
        body: String,
    ): Mono<GitLabDiscussionNoteCreateResponse> =
        gl.postMono(
            GitLabDiscussionNoteCreateResponse::class.java,
            { uri ->
                uri.pathSegment(
                    "projects",
                    projectId,
                    "merge_requests",
                    mrId,
                    "discussions",
                    threadId,
                    "notes",
                )
            },
            GitLabDiscussionNoteCreateRequest(body),
        )

    fun resolveDiscussion(
        projectId: String,
        mrId: String,
        threadId: String,
    ): Mono<GitLabDiscussion> =
        gl.putMono(
            GitLabDiscussion::class.java,
            { uri ->
                uri.pathSegment(
                    "projects",
                    projectId,
                    "merge_requests",
                    mrId,
                    "discussions",
                    threadId,
                )
            },
            mapOf("resolved" to true),
        )

    fun approve(
        projectId: String,
        mrId: String,
    ): Mono<GitLabApproveResponse> =
        gl.postMono(
            GitLabApproveResponse::class.java,
            { uri ->
                uri.pathSegment("projects", projectId, "merge_requests", mrId, "approve")
            },
            emptyMap<String, String>(),
        )

    fun merge(
        projectId: String,
        mrId: String,
        sha: String,
    ): Mono<GitLabMergeResponse> =
        gl.putMono(
            GitLabMergeResponse::class.java,
            { uri ->
                uri
                    .pathSegment("projects", projectId, "merge_requests", mrId, "merge")
                    .queryParam("sha", sha)
                    .queryParam("should_remove_source_branch", true)
            },
            emptyMap<String, Any>(),
        )
}
