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
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateRequest
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateResponse
import de.gematik.zts.templateeditor.domain.reviews.CommentItem
import de.gematik.zts.templateeditor.domain.reviews.CommentsPayload
import de.gematik.zts.templateeditor.domain.reviews.ReviewsOverviewItem
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import de.gematik.zts.templateeditor.gitlab.mapGitLabErrors
import de.gematik.zts.templateeditor.gitlab.mapping.review.GitLabDiscussionCreateRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class ReviewsService(
    private val client: GitLabReviewsClient,
    private val indexService: ResourceIndexService,
    private val resolver: CommentFileResolver,
    private val reviewerAuthorization: ReviewerAuthorization,
) {
    fun list(
        state: String,
        page: Int,
        size: Int,
    ): Flux<ReviewsOverviewItem> =
        client
            .listMergeRequests(state, page, size)
            .mapGitLabErrors()
            .map { mr ->
                ReviewsOverviewItem(
                    mr = MergeRequestRef(mr.iid, mr.webUrl),
                    projectId = mr.projectId.toString(),
                )
            }

    fun listComments(
        repo: String,
        mrId: String,
        branch: String,
        version: String,
    ): Mono<CommentsPayload> {
        val indexMono = indexService.load(repo, branch, version).cache()

        return client
            .getDiscussions(repo, mrId)
            .mapGitLabErrors()
            .flatMap { discussion ->
                Flux
                    .fromIterable(discussion.notes)
                    .filter { it.system != true }
                    .flatMap { note ->
                        val pos = note.position ?: return@flatMap Mono.empty()
                        val path = pos.newPath ?: pos.oldPath ?: return@flatMap Mono.empty()

                        indexMono.map { idx ->
                            val lookup = idx.asLookup()
                            val (type, file) = resolver.resolveFromGitLabPath(path, version, lookup)

                            CommentItem(
                                id = note.id.toString(),
                                fileName = file,
                                type = type,
                                line = pos.newLine ?: pos.oldLine,
                                body = note.body,
                                author = note.author?.name ?: note.author?.username,
                                createdAt = note.createdAt,
                                resolved = note.resolved ?: discussion.resolved ?: false,
                                threadId = discussion.id,
                                isInline = true,
                            )
                        }
                    }
            }.collectList()
            .map(::CommentsPayload)
    }

    fun createComment(req: CommentCreateRequest): Mono<CommentCreateResponse> {
        require(req.line != null) { "line must not be null" }
        require(req.body.isNotBlank()) { "body must not be blank" }
        val indexMono = indexService.load(req.repositoryId, req.branch, req.version).cache()

        return Mono
            .zip(
                indexMono,
                client.getMergeRequestDetails(req.repositoryId, req.mrId).mapGitLabErrors(),
            ).flatMap { tuple ->
                val path =
                    resolver.resolveGitLabPath(
                        req.fileName,
                        req.type,
                        req.version,
                        tuple.t1.asLookup(),
                    )

                client
                    .createDiscussion(
                        req.repositoryId,
                        req.mrId,
                        GitLabDiscussionCreateRequest(
                            body = req.body,
                            position =
                                GitLabDiscussionCreateRequest.Position(
                                    positionType = "text",
                                    baseSha = tuple.t2.diffRefs.baseSha,
                                    startSha = tuple.t2.diffRefs.startSha,
                                    headSha = tuple.t2.diffRefs.headSha,
                                    newPath = path,
                                    newLine = req.line,
                                ),
                        ),
                    ).mapGitLabErrors()
            }.map { CommentCreateResponse(it.id) }
    }

    fun reply(
        repo: String,
        mrId: String,
        threadId: String,
        body: String,
        resolved: Boolean,
    ): Mono<CommentCreateResponse> =
        client
            .createNote(repo, mrId, threadId, body)
            .mapGitLabErrors()
            .flatMap { note ->
                if (!resolved) {
                    Mono.just(note)
                } else {
                    client
                        .resolveDiscussion(repo, mrId, threadId)
                        .mapGitLabErrors()
                        .thenReturn(note)
                }
            }.map { CommentCreateResponse(it.id.toString()) }

    fun approveAndMerge(
        repo: String,
        mrId: String,
    ): Mono<String> =
        reviewerAuthorization
            .ensureReviewerState(true)
            .then(
                client
                    .approve(repo, mrId)
                    .mapGitLabErrors()
                    .onErrorResume(GitLabHttpException::class.java) { ex ->
                        if (ex.status == 401) Mono.empty() else Mono.error(ex)
                    }.then(
                        client
                            .getMergeRequestDetails(repo, mrId)
                            .mapGitLabErrors()
                            .delayElement(Duration.ofSeconds(1))
                            .flatMap { details -> client.merge(repo, mrId, details.sha).mapGitLabErrors() },
                    ).thenReturn("approved + auto-merge triggered"),
            )
}

fun ResourceIndex.asLookup(): ResourceIndexLookup =
    object : ResourceIndexLookup {
        override fun urlForMarkdown(md: String): String? {
            val firstOrNull = items.firstOrNull { it.markdownFile == md }
            return if (firstOrNull != null) "${firstOrNull.version};${firstOrNull.url}" else null
        }

        override fun markdownForUrl(
            url: String,
            version: String,
        ) = items.firstOrNull { it.url == url && it.version == version }?.markdownFile
    }
