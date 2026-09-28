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
import org.springframework.stereotype.Component
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

/**
 * Adapter for reading version information from a GitLab repository.
 *
 * Assumes a directory structure of the form:
 *   <version>/
 *
 * Reads the repository tree recursively and creates `VersionItem` instances
 * containing the resource name, version string, and the last commit timestamp
 * affecting that version path.
 */
@Component
class VersionsAdapter(
    private val gl: GitLabClient,
    private val reviewerAuthorization: ReviewerAuthorization,
) {
    /**
     * Lists all available versions for the given project.
     *
     * @param projectId GitLab project ID or path, as accepted by the GitLab API.
     * @param workspace Optional branch name. If null or blank, the project's default branch is resolved.
     *
     * @return `Flux<VersionItem>` where each item describes:
     *   - `version`: second-level directory, must match a semver-like pattern.
     *   - `lastModified`: timestamp of the most recent commit touching that path.
     *
     * Behavior:
     *   - If GitLab returns 404, an empty stream is returned.
     *   - Other errors are propagated.
     */
    fun list(
        projectId: String,
        workspace: String?,
    ): Flux<VersionItem> =
        resolveBranch(projectId, workspace)
            .mapGitLabErrors()
            .flatMapMany { branch ->
                gl
                    .getFlux(clazz = GitLabTreeItemDto::class.java) { uri ->
                        uri.buildGitLabVersionsUri(projectId, branch)
                    }.mapGitLabErrors()
                    .onErrorResume(GitLabNotFoundException::class.java) { Flux.empty() }
                    .filter { isDirectory(it) }
                    .mapNotNull { it.path }
                    .filter { isSemverLike(it) }
                    .flatMap { version ->
                        lastModifiedFor(projectId, branch, version)
                            .mapGitLabErrors()
                            .map { ts -> toVersionItem(version, ts) }
                    }
            }

    /**
     * Deletes the specified version by creating a commit that removes the corresponding directory.
     *
     * Authorization:
     *   - Reviewers are not allowed to delete versions. If the current user is a reviewer,
     *     a ForbiddenException is raised (mapped to HTTP 403 by the web layer).
     *
     * Behavior:
     *   - If the version does not exist (404), the Mono completes without error.
     *   - If branch deletion returns 404, it is treated as already deleted and ignored.
     *   - Other errors are propagated.
     *
     * Branch behavior:
     *   - The deletion commit is created on the resolved branch (workspace if provided, otherwise default branch).
     *   - If the resolved branch is neither "main" nor "dev", the branch is deleted afterwards
     *     via GitLab's branch deletion endpoint.
     *
     * @param projectId GitLab project ID or path, as accepted by the GitLab API.
     * @param workspace Optional branch name. If null or blank, the project's default branch is resolved.
     * @param version The version to delete, corresponding to a directory in the repository.
     *
     * @return `Mono<Unit>` that completes when the deletion commit (and optional branch deletion) is done.
     */
    fun deleteVersion(
        projectId: String,
        workspace: String?,
        version: String,
    ): Mono<Unit> =
        reviewerAuthorization
            .ensureReviewerState(false)
            .then(resolveBranch(projectId, workspace))
            .mapGitLabErrors()
            .flatMap { branch ->
                val body =
                    mapOf(
                        "branch" to branch,
                        "commit_message" to "Delete version $version",
                        "actions" to
                            listOf(
                                mapOf("action" to "delete", "file_path" to version),
                            ),
                    )

                gl
                    .postMono(
                        GitLabCommitDto::class.java,
                        { uri -> uri.pathSegment("projects", projectId, "repository", "commits") },
                        body,
                    ).mapGitLabErrors()
                    .onErrorResume(GitLabNotFoundException::class.java) { Mono.empty() }
                    .then(deleteBranchIfNeeded(projectId, branch))
            }.thenReturn(Unit)

    private fun isDirectory(item: GitLabTreeItemDto): Boolean = item.type == "tree" && item.path != null

    private fun toVersionItem(
        version: String,
        ts: OffsetDateTime,
    ): VersionItem =
        VersionItem(
            version = version,
            lastModified = ts,
        )

    private fun UriBuilder.buildGitLabVersionsUri(
        projectId: String,
        branch: String,
    ) = apply {
        pathSegment("projects", projectId, "repository", "tree")
        queryParam("ref", branch)
        queryParam("recursive", "true")
        queryParam("per_page", 2000)
    }

    private fun resolveBranch(
        projectId: String,
        workspace: String?,
    ): Mono<String> {
        if (!workspace.isNullOrBlank()) return Mono.just(workspace)

        return gl
            .getFlux(clazz = GitLabProjectDetailsDto::class.java) { uri ->
                uri.pathSegment("projects", projectId)
            }.mapGitLabErrors()
            .onErrorResume(GitLabNotFoundException::class.java) { Flux.empty() }
            .next()
            .map { it.defaultBranch ?: "main" }
            .switchIfEmpty(Mono.just("main"))
    }

    private fun lastModifiedFor(
        projectId: String,
        branch: String,
        path: String,
    ): Mono<OffsetDateTime> =
        gl
            .getFlux(clazz = GitLabCommitDto::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "repository", "commits")
                    .queryParam("ref_name", branch)
                    .queryParam("path", path)
                    .queryParam("per_page", 1)
            }.mapGitLabErrors()
            .onErrorResume(GitLabNotFoundException::class.java) { Flux.empty() }
            .next()
            .filter { it.committedDate != null }
            .map { OffsetDateTime.parse(it.committedDate!!) }
            .switchIfEmpty(Mono.just(OffsetDateTime.MIN))

    private fun deleteBranchIfNeeded(
        projectId: String,
        branch: String,
    ): Mono<Unit> {
        if (branch == "main" || branch == "dev") return Mono.empty()

        return gl
            .deleteMono(
                Void::class.java,
            ) { uri ->
                uri.pathSegment("projects", projectId, "repository", "branches", branch)
            }.mapGitLabErrors()
            .onErrorResume(GitLabNotFoundException::class.java) { Mono.empty() }
            .thenReturn(Unit)
    }

    private fun isSemverLike(version: String): Boolean = version.matches(Regex("""^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$"""))
}
