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

package de.gematik.zts.templateeditor.gitlab.commits

import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.GitLabCommitAction
import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndexEntry
import de.gematik.zts.templateeditor.domain.workspaces.toGitLabAction
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import de.gematik.zts.templateeditor.logging.logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/**
 * Builds GitLab commit actions for workspace changes.
 *
 * This class generates {@link GitLabCommitAction} objects based on a set of {@link CommitChange}
 * instances. Markdown template files receive special handling — each change not only produces
 * a commit action for the markdown file itself, but also updates a resource index file
 * (`index.json`) which tracks available markdown documents and their version mapping.
 *
 * The resulting actions can then be submitted together as a single GitLab commit.
 */
@Component
class GitLabActionBuilder(
    private val gl: GitLabClient,
    private val mapper: ObjectMapper,
) {
    private val log by logger()

    /**
     * Builds a list of GitLab commit actions for the given changes.
     *
     * - Non-markdown changes are directly translated into {@link GitLabCommitAction}.
     * - Template-Markdown changes generate:
     *   - a commit action for the markdown file
     *   - an update to the central resource index (`index.json`)
     *     where entries are inserted, updated, or removed.
     *
     * If no markdown changes exist, the resource index is not read or modified.
     * Only non-markdown commit actions will be returned in that case.
     *
     * @param projectId   GitLab project ID used to load the existing index file.
     * @param branch      Target branch for the commit.
     * @param version     Version of the resource group (used in directory structure and filenames).
     * @param changes     Domain changes to be translated into GitLab commit operations.
     *
     * @return Mono emitting the full list of {@link GitLabCommitAction}s to be committed,
     *         including index updates when markdown changes are present.
     */
    fun buildGitLabActions(
        projectId: String,
        branch: String,
        version: String,
        changes: List<CommitChange>,
    ): Mono<List<GitLabCommitAction>> {
        log.info("Preparing commit actions for project={} branch={} version={}", projectId, branch, version)
        log.debug("Received {} changes: {}", changes.size, changes.map { it.action to it.fileName })

        val (mdChanges, otherChanges) = changes.partition { it.type == CommitChange.Type.TEMPLATE_MARKDOWN }

        val otherActions = otherChanges.map { it.toGitLabAction(version) }

        val mdPairs = mdChanges.map { buildSingleMarkdownFileAction(it, version) }
        val mdFileActions = mdPairs.map { it.first }
        val mutations = mdPairs.map { it.second }

        if (mdPairs.isEmpty()) {
            return Mono.just(otherActions)
        }

        return loadIndex(projectId, branch, version)
            .map { (idx, existed) ->
                applyMutations(idx, mutations)
                val indexAction = saveIndex(idx, existed, version)
                log.info("Index update action generated ({} entries total)", idx.items.size)
                otherActions + mdFileActions + indexAction
            }
    }

    private sealed interface IndexMutation {
        data class Upsert(
            val url: String,
            val version: String,
            val mdVersion: String,
            val mdFile: String,
        ) : IndexMutation

        data class Remove(
            val url: String,
            val version: String,
            val mdVersion: String,
        ) : IndexMutation
    }

    private fun applyMutations(
        idx: ResourceIndex,
        mutations: List<IndexMutation>,
    ) {
        mutations.forEach { mutation ->
            when (mutation) {
                is IndexMutation.Upsert -> {
                    log.debug("UPSERT url={} v={} file={}", mutation.url, mutation.version, mutation.mdFile)
                    val index = idx.items.indexOfFirst { it.url == mutation.url && it.version == mutation.mdVersion }
                    val entry = ResourceIndexEntry(mutation.url, mutation.mdVersion, mutation.mdFile)
                    if (index >= 0) idx.items[index] = entry else idx.items.add(entry)
                }

                is IndexMutation.Remove -> {
                    log.debug("REMOVE url={} v={}", mutation.url, mutation.version)
                    val newItems = idx.items.filterNot { it.url == mutation.url }
                    idx.items.clear()
                    idx.items.addAll(newItems)
                }
            }
        }
    }

    private fun buildSingleMarkdownFileAction(
        ch: CommitChange,
        version: String,
    ): Pair<GitLabCommitAction, IndexMutation> {
        require(ch.fileName.contains(";")) {
            "Invalid fileName for TEMPLATE_MARKDOWN change: ${ch.fileName}"
        }

        val split = ch.fileName.split(";") // IF TEMPLATE_MARKDOWN THEN fileName == VERSION;URL
        val mdVersion = split[0]
        val url = split[1]
        val mdFile = makeMarkdownFileName(url, version, mdVersion)
        val mdRepoPath = mdPath(version, mdFile)
        val enc = if (ch.encoding == CommitChange.Encoding.BASE64) "base64" else "text"

        return when (ch.action) {
            CommitChange.Action.CREATE -> {
                GitLabCommitAction(
                    action = "create",
                    filePath = mdRepoPath,
                    content = ch.content ?: "",
                    encoding = enc,
                ) to IndexMutation.Upsert(url, version, mdVersion, mdFile)
            }

            CommitChange.Action.UPDATE -> {
                GitLabCommitAction(
                    action = "update",
                    filePath = mdRepoPath,
                    content = ch.content ?: "",
                    encoding = enc,
                ) to IndexMutation.Upsert(url, version, mdVersion, mdFile)
            }

            CommitChange.Action.DELETE -> {
                GitLabCommitAction(
                    action = "delete",
                    filePath = mdRepoPath,
                    encoding = enc,
                ) to IndexMutation.Remove(url, mdVersion, version)
            }
        }
    }

    private fun loadIndex(
        projectId: String,
        branch: String,
        version: String,
    ): Mono<Pair<ResourceIndex, Boolean>> =
        gl
            .fetchFileTextInSingleLine(projectId, branch, indexPath(version))
            .map { raw ->
                try {
                    mapper.readValue(raw, ResourceIndex::class.java) to true
                } catch (_: Exception) {
                    log.warn("Failed parsing index.json")
                    ResourceIndex(mutableListOf()) to true
                }
            }.switchIfEmpty(
                Mono.fromCallable {
                    log.info("No index.json present. Creating new index")
                    ResourceIndex(mutableListOf()) to false
                },
            ).onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    log.info("Index not found (404). Will create new index.json")
                    Mono.just(ResourceIndex(mutableListOf()) to false)
                } else {
                    log.error("Failed loading index: ${ex.message}", ex)
                    Mono.error(ex)
                }
            }

    private fun saveIndex(
        idx: ResourceIndex,
        existed: Boolean,
        version: String,
    ): GitLabCommitAction {
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(idx)
        return GitLabCommitAction(
            action = if (existed) "update" else "create",
            filePath = indexPath(version),
            content = json,
            encoding = "text",
        )
    }

    private fun slugFromUrl(url: String): String = url.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun makeMarkdownFileName(
        url: String,
        version: String,
        mdVersion: String,
    ): String = "${slugFromUrl(url)}-$version-$mdVersion.md"

    private fun indexPath(version: String) = "$version/metadata/website/resources-description/index.json"

    private fun mdPath(
        version: String,
        mdFile: String,
    ) = "$version/metadata/website/resources-description/$mdFile"
}
