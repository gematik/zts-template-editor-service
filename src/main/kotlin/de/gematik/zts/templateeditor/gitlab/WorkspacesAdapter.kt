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
import de.gematik.zts.templateeditor.domain.reviews.ReviewRequest
import de.gematik.zts.templateeditor.domain.workspaces.BranchItem
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.CommitRequest
import de.gematik.zts.templateeditor.domain.workspaces.CommitResponse
import de.gematik.zts.templateeditor.domain.workspaces.GitLabCommitResp
import de.gematik.zts.templateeditor.domain.workspaces.GitLabCreateCommitReq
import de.gematik.zts.templateeditor.domain.workspaces.GitLabMergeRequestResp
import de.gematik.zts.templateeditor.domain.workspaces.GitLabProjectResp
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.WorkspaceDetails
import de.gematik.zts.templateeditor.gitlab.commits.GitLabActionBuilder
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabNotFoundException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabUpstreamException
import de.gematik.zts.templateeditor.gitlab.fetcher.DetailFetcher
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabBranchDto
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabCommitMiniDto
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import de.gematik.zts.templateeditor.gitlab.validation.CommitContentProperties
import de.gematik.zts.templateeditor.gitlab.validation.FileTypeValidator
import de.gematik.zts.templateeditor.logging.logger
import io.netty.buffer.PooledByteBufAllocator
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.core.io.buffer.NettyDataBufferFactory
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Base64
import java.util.concurrent.Executor

@Component
class WorkspacesAdapter(
    private val gl: GitLabClient,
    private val detailFetcher: DetailFetcher,
    private val actionBuilder: GitLabActionBuilder,
    private val commitContentProperties: CommitContentProperties,
    private val fileTypeValidator: FileTypeValidator,
    private val reviewerAuthorization: ReviewerAuthorization,
) {
    private val log by logger()
    // executor for blocking operations (DataBufferUtils.outputStreamPublisher())
    private val blockingExecutor = Executor { task -> Schedulers.boundedElastic().schedule(task) }

    /**
     * Lists all branches in the specified repository that have changes
     * affecting the given version path.
     *
     * @param repositoryId GitLab project ID or path.
     *
     * @return A [Flux] emitting [BranchItem]s for each branch with relevant changes.
     *         If no branches are found, an empty stream is returned.
     *         If a 404 error occurs, it is treated as no branches found.
     *         Other errors are propagated.
     */
    fun listBranches(repositoryId: String): Flux<BranchItem> =
        gl
            .getFlux(clazz = GitLabBranchDto::class.java) { uri ->
                fetchBranchUriBuilder(uri, repositoryId)
            }.mapGitLabErrors()
            .flatMap { branchDto -> lastChangeForBranch(repositoryId, branchDto) }
            .onErrorResume(GitLabNotFoundException::class.java) { Flux.empty() }

    /**
     * Fetches detailed information about a specific workspace version
     * in the given repository and branch.
     *
     * @param repositoryId GitLab project ID or path.
     * @param branch Branch name.
     * @param version Version string (second-level directory).
     *
     * @return A [Mono] emitting [WorkspaceDetails] for the specified version.
     */
    fun details(
        repositoryId: String,
        branch: String,
        version: String,
    ): Mono<WorkspaceDetails> = detailFetcher.details(repositoryId, branch, version)

    /**
     * Creates a merge request for an existing version.
     *
     * @param request The [ReviewRequest] containing commit details.
     *
     * @return A [Mono] emitting [CommitResponse] with commit and merge request info.
     */
    fun review(request: ReviewRequest): Mono<MergeRequestRef> {
        val projectId = request.repo
        val branch = request.branch
        val title = request.title?.ifBlank { "Update via Template Editor" } ?: "Update via Template Editor"

        val permissionGuard = reviewerAuthorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch)

        return permissionGuard
            .then(ensureBranchExists(projectId, branch))
            .then(createMergeRequest(projectId, branch, title))
    }

    /**
     * Commits changes to the specified branch in the repository.
     * Optionally creates a merge request for the committed changes.
     *
     * @param request The [CommitRequest] containing commit details.
     *
     * @return A [Mono] emitting [CommitResponse] with commit and merge request info.
     */
    fun commit(request: CommitRequest): Mono<CommitResponse> {
        val projectId = request.repositoryId
        val branch = request.branch
        val ver = request.version

        val permissionGuard = reviewerAuthorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch)
        val branchReady = permissionGuard.then(ensureBranchExists(projectId, branch))

        val actionsMono =
            branchReady.then(
                actionBuilder.buildGitLabActions(
                    projectId,
                    branch,
                    ver,
                    request.changes,
                ),
            )

        val commitMono: Mono<GitLabCommitResp> =
            actionsMono.flatMap { actions ->
                gl
                    .postMono(
                        GitLabCommitResp::class.java,
                        { uri -> uri.pathSegment("projects", projectId, "repository", "commits") },
                        GitLabCreateCommitReq(branch, request.message, actions),
                    ).doOnError { ex ->
                        log.error(
                            "GitLab commit FAILED projectId={} branch={} message='{}'\nactions:\n{}",
                            projectId,
                            branch,
                            request.message,
                            actions.map { action -> action.filePath },
                            ex,
                        )
                    }.mapGitLabErrors()
            }

        val mrMono: Mono<MergeRequestRef> =
            if (!request.createMergeRequest) {
                Mono.just(MergeRequestRef(-1, ""))
            } else {
                createMergeRequest(projectId, branch, request.message.ifBlank { "Update via Template Editor" })
            }

        return Mono
            .zip(commitMono, mrMono)
            .map { tuple ->
                CommitResponse(
                    commitId = tuple.t1.id,
                    head = branch,
                    mergeRequest = tuple.t2,
                )
            }
    }

    /**
     * Commits a file as base64 content to the specified branch in the repository.
     *
     * The upload is streamed to a temporary file in [CommitContentProperties.tempDir]
     * to avoid holding the full payload in heap.
     * The temp file is always deleted after the commit completes (or fails).
     *
     * @param repositoryId GitLab project ID or path.
     * @param branch Branch name.
     * @param version Version string (second-level directory).
     * @param message Commit message.
     * @param action Action type ("create" or "update").
     * @param file The file to be committed.
     * @param fileName The name of the file in the repository (e.g., "input.zip").
     *
     * @return A [Mono] emitting [CommitResponse] with commit and merge request info.
     */
    fun commitFile(
        repositoryId: String,
        branch: String,
        version: String,
        message: String,
        action: String,
        file: FilePart,
        fileName: String,
    ): Mono<CommitResponse> {
        val act = CommitChange.Action.from(action)
        require(act == CommitChange.Action.CREATE || act == CommitChange.Action.UPDATE) {
            "action must be create or update"
        }

        require(
            fileName.matches(Regex("^(?!/)(?!.*\\\\)(?!.*(^|/)\\.\\.(/|$))[A-Za-z0-9._\\-/;]+$")),
        ) { "fileName contains illegal characters" }

        return reviewerAuthorization
            .ensureReviewerHasOpenMrOrUnauthorized(repositoryId, branch)
            .then(ensureBranchExists(repositoryId, branch))
            .then(
                streamToTempFileAndCommit(repositoryId, branch, version, message, act, file, fileName),
            )
    }

    /**
     * Streams the multipart upload to a temp file, validates it, Base64-encodes
     * it, and commits directly via the GitLab commit API.
     *
     * Uses [Mono.usingWhen] to guarantee the temp file is deleted on success,
     * error, or cancellation.
     *
     * Bypasses the CommitRequest -> commit() → buildGitLabActions chain.
     * Constructs GitLabCommitAction directly, eliminating intermediate data class copies.
     */
    private fun streamToTempFileAndCommit(
        repositoryId: String,
        branch: String,
        version: String,
        message: String,
        act: CommitChange.Action,
        file: FilePart,
        fileName: String,
    ): Mono<CommitResponse> {
        val tempDir = commitContentProperties.tempDir

        // Phase 1: create temp file (cold, deferred until subscription)
        val createTempFile: Mono<Path> =
            Mono
                .fromCallable {
                    Files.createTempFile(tempDir, "upload-", ".tmp")
                }.subscribeOn(Schedulers.boundedElastic())

        // Phase 3: cleanup — always delete the temp file
        val cleanup: (Path) -> Mono<Void> = { tempPath ->
            Mono
                .fromRunnable<Void> {
                    Files.deleteIfExists(tempPath)
                }.subscribeOn(Schedulers.boundedElastic())
                .then()
        }

        return Mono.usingWhen(
            createTempFile,
            // Phase 2: stream upload → validate → stream commit directly to GitLab
            { tempPath ->
                file
                    .transferTo(tempPath)
                    .then(validateTempFile(tempPath, fileName))
                    .then(
                        Mono.defer {
                            val filePath = "$version/input/$fileName"
                            val bodyStream = buildStreamingCommitBody(
                                branch, message, act.value, filePath, tempPath,
                            )
                            gl
                                .postMonoStreaming(
                                    GitLabCommitResp::class.java,
                                    { uri -> uri.pathSegment("projects", repositoryId, "repository", "commits") },
                                    bodyStream,
                                ).doOnError { ex ->
                                    log.error(
                                        "GitLab file commit FAILED projectId={} branch={} file='{}'",
                                        repositoryId,
                                        branch,
                                        filePath,
                                        ex,
                                    )
                                }.mapGitLabErrors()
                                .map { resp -> CommitResponse(commitId = resp.id, head = branch) }
                        },
                    )
            },
            cleanup, // success cleanup
            { tempPath, _ -> cleanup(tempPath) }, // error cleanup
            cleanup, // cancel cleanup
        )
    }

    /**
     * Validates the temp file (size check + content-type check) without loading it into heap.
     */
    private fun validateTempFile(
        tempPath: Path,
        fileName: String,
    ): Mono<Void> =
        Mono
            .fromRunnable<Void> {
                val fileSize = Files.size(tempPath)
                require(fileSize <= commitContentProperties.maxLength) { "File too large" }

                Files.newInputStream(tempPath).use { stream ->
                    checkCommitFileStream(fileName, stream)
                }
            }.subscribeOn(Schedulers.boundedElastic())
            .then()

    /**
     * Builds a [Flux] of [DataBuffer] that produces a complete GitLab commit JSON body,
     * streaming the file content as base64 directly from disk.
     *
     * Memory footprint: constant, regardless of file size. Chunks are read from disk,
     * base64 encoded live through the output stream, and discarded immediately.
     * Only small buffers are used during processing.
     */
    private fun buildStreamingCommitBody(
        branch: String,
        message: String,
        action: String,
        filePath: String,
        tempPath: Path,
    ): Flux<DataBuffer> {
        // NettyDataBufferFactory uses PooledByteBufAllocator -> reduces GC pressure
        val factory = NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT)

        // JSON prefix: everything up to (and including) the opening quote of the content value
        val prefix = buildString {
            append("""{"branch":""")
            append('"').append(escapeJsonValue(branch)).append('"')
            append(""","commit_message":""")
            append('"').append(escapeJsonValue(message)).append('"')
            append(""","actions":[{"action":""")
            append('"').append(escapeJsonValue(action)).append('"')
            append(""","file_path":""")
            append('"').append(escapeJsonValue(filePath)).append('"')
            append(""","content":"""")
        }.toByteArray(Charsets.UTF_8)
        // JSON suffix: closing quote + remaining fields + close braces
        val suffix = """","encoding":"base64"}]}""".toByteArray(Charsets.UTF_8)

        // outputStreamPublisher calls the writer on blockingExecutor and transforms the result into a Flux<DataBuffer>
        val base64Publisher = DataBufferUtils.outputStreamPublisher(
            { outputStream ->
                outputStream.write(prefix)

                val nonClosingOutputStream = StreamUtils.nonClosing(outputStream)
                Files.newInputStream(tempPath).use { inputStream ->
                    // reuse ByteBuf with .wrap() -> no extra padding needed
                    Base64.getEncoder().wrap(nonClosingOutputStream).use { base64OutputStream ->
                        // direct transformation, to minimize byte array allocations
                        inputStream.transferTo(base64OutputStream)
                    }
                }
                outputStream.write(suffix)
            },
            factory,
            // protect file processing off the reactive event loop
            blockingExecutor,
            16 * 1024, // Stream file → Base64 in ~12 KB (16KB with encoding) chunks (multiple of 3 for clean base64 boundaries)
        )

        return Flux.from(base64Publisher)
    }

    /**
     * Minimal JSON string escaping for values known to be simple (branch names, paths, messages).
     */
    private fun escapeJsonValue(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * Opens a workspace by fetching its details based on the merge request ID.
     *
     * @param projectId GitLab project ID or path.
     * @param mrId Merge request ID.
     * @param version Version string (second-level directory).
     *
     * @return A [Mono] emitting [WorkspaceDetails] for the specified merge request.
     */
    fun openByMr(
        projectId: String,
        mrId: Int,
        version: String,
    ): Mono<WorkspaceDetails> =
        gl
            .getFlux(clazz = GitLabMergeRequestDto::class.java) { uri ->
                uri.pathSegment("projects", projectId, "merge_requests", mrId.toString())
            }.mapGitLabErrors()
            .next()
            .switchIfEmpty(Mono.error(GitLabNotFoundException()))
            .flatMap { mr ->
                val branch =
                    mr.sourceBranch
                        ?: return@flatMap Mono.error(GitLabUpstreamException(message = "MR has no SourceBranch"))
                details(projectId, branch, version)
            }

    private fun createMergeRequest(
        projectId: String,
        branch: String,
        title: String,
    ): Mono<MergeRequestRef> =
        gl
            .getFlux(clazz = GitLabProjectResp::class.java) { uri -> uri.pathSegment("projects", projectId) }
            .next()
            .flatMap { projectResp ->
                mapToMergeRequestResponse(projectResp, branch, projectId, title)
            }.onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("409") == true || ex.message?.contains("404") == true) {
                    Mono.empty()
                } else {
                    Mono.error(ex)
                }
            }.switchIfEmpty(Mono.just(MergeRequestRef(-1, "")))

    private fun ensureBranchExists(
        projectId: String,
        branch: String,
    ): Mono<GitLabBranchDto> =
        gl
            .getFlux(clazz = GitLabBranchDto::class.java) { uri ->
                uri.pathSegment("projects", projectId, "repository", "branches", branch)
            }.next()
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    gl
                        .getFlux(clazz = GitLabProjectResp::class.java) { uri ->
                            uri.pathSegment("projects", projectId)
                        }.mapGitLabErrors()
                        .next()
                        .flatMap { proj -> createBranch(projectId, branch, proj) }
                } else {
                    Mono.error(ex)
                }
            }

    private fun createBranch(
        projectId: String,
        branch: String,
        proj: GitLabProjectResp,
    ): Mono<GitLabBranchDto> =
        gl
            .postMono(
                GitLabBranchDto::class.java,
                { uri ->
                    uri
                        .pathSegment("projects", projectId, "repository", "branches")
                        .queryParam("branch", branch)
                        .queryParam("ref", proj.defaultBranch)
                },
                emptyMap<String, Any>(),
            ).mapGitLabErrors()

    private fun mapToMergeRequestResponse(
        projectResp: GitLabProjectResp,
        branch: String,
        projectId: String,
        title: String,
    ): Mono<MergeRequestRef> =
        if (projectResp.defaultBranch == branch) {
            Mono.empty()
        } else {
            gl
                .postMono(
                    GitLabMergeRequestResp::class.java,
                    { uri ->
                        uri
                            .pathSegment("projects", projectId, "merge_requests")
                            .queryParam("source_branch", branch)
                            .queryParam("target_branch", projectResp.defaultBranch)
                            .queryParam("title", title)
                            .queryParam("remove_source_branch", "false")
                    },
                    emptyMap<String, Any>(),
                ).map { mr ->
                    MergeRequestRef(id = mr.id, webUrl = mr.webUrl)
                }
        }

    private fun fetchBranchUriBuilder(
        uri: UriBuilder,
        repositoryId: String,
    ): UriBuilder =
        uri
            .pathSegment("projects", repositoryId, "repository", "branches")
            .queryParam("per_page", 100)

    private fun lastChangeForBranch(
        projectId: String,
        branchDto: GitLabBranchDto,
    ): Mono<BranchItem> =
        gl
            .getFlux(clazz = GitLabCommitMiniDto::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "repository", "commits")
                    .queryParam("ref_name", branchDto.name)
                    .queryParam("per_page", 1)
            }.next()
            .flatMap { dto ->
                findMergeRequestForBranch(projectId, branchDto.name)
                    .map { mrRef ->
                        BranchItem(
                            branch = branchDto.name,
                            lastModified = dto.committedDate?.let(OffsetDateTime::parse),
                            author = dto.authorName,
                            default = branchDto.default,
                            protected = branchDto.protected,
                            mergeRequestRef = mrRef,
                        )
                    }.switchIfEmpty(
                        Mono.just(
                            BranchItem(
                                branch = branchDto.name,
                                lastModified = dto.committedDate?.let(OffsetDateTime::parse),
                                author = dto.authorName,
                                default = branchDto.default,
                                protected = branchDto.protected,
                                mergeRequestRef = null,
                            ),
                        ),
                    )
            }.onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) Mono.empty() else Mono.error(ex)
            }

    private fun findMergeRequestForBranch(
        projectId: String,
        branch: String,
    ): Mono<MergeRequestRef> =
        gl
            .getFlux(clazz = GitLabMergeRequestDto::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "merge_requests")
                    .queryParam("state", "opened")
                    .queryParam("source_branch", branch)
                    .queryParam("per_page", 1)
            }.mapGitLabErrors()
            .next()
            .map { mr -> MergeRequestRef(id = mr.iid, webUrl = mr.webUrl) }
            .onErrorResume(GitLabNotFoundException::class.java) { Mono.empty() }

    /**
     * Validates the uploaded file content using InputStream-based validators.
     * Only reads the minimum amount of data needed for each file type check.
     */
    private fun checkCommitFileStream(
        fileName: String,
        input: InputStream,
    ) {
        when {
            fileName.endsWith(".zip", ignoreCase = true) -> {
                require(fileTypeValidator.isFileTypeZip(input))
                { "File is not a valid ZIP file" }
            }

            fileName.endsWith(".json", ignoreCase = true) -> {
                require(fileTypeValidator.isValidFhirJson(input))
                {
                    "The file is not valid FHIR JSON. JSON must have the correct syntax and the ResourceType must be ValueSet, ConceptMap, or CodeSystem."
                }
            }

            fileName.endsWith(".xml", ignoreCase = true) -> {
                require(fileTypeValidator.isValidFhirXml(input))
                {
                    "The file is not valid FHIR XML. XML must have the correct syntax and the root tag must be \"ValueSet,\" \"ConceptMap,\" or \"CodeSystem.\""
                }
            }

            else -> {
                throw IllegalArgumentException("Invalid data type: '.zip', '.xml', and '.json' are permitted.")
            }
        }
    }
}
