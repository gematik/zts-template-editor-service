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

package de.gematik.zts.templateeditor.gitlab.fetcher

import com.fasterxml.jackson.annotation.JsonProperty
import de.gematik.zts.templateeditor.domain.workspaces.InputFile
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.domain.workspaces.TemplateJsonItem
import de.gematik.zts.templateeditor.domain.workspaces.TemplateMdItem
import de.gematik.zts.templateeditor.domain.workspaces.WorkspaceDetails
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import de.gematik.zts.templateeditor.gitlab.mapping.GitLabTreeItemDto
import de.gematik.zts.templateeditor.logging.logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.util.Optional

/**
 * Fetches detailed workspace information from GitLab, including template JSONs,
 * markdown files, metadata, changelogs, and open merge request references.
 */
@Component
class DetailFetcher(
    private val gl: GitLabClient,
    private val mapper: ObjectMapper,
) {
    private val log by logger()

    /**
     * Fetches detailed information for a specific workspace version.
     *
     * This includes:
     * - Template JSON files located in `$VERSION/input/templates/`
     * - Markdown files as specified in the resource index
     * - Package template JSON
     * - Metadata JSON
     * - Changelog JSON
     * - Various markdown notes from the metadata editor directory
     * - An optional reference to any open merge request for the branch
     *
     * @param projectId GitLab project ID
     * @param branch    GitLab branch name
     * @param version   Workspace version path segment
     * @return Mono emitting the aggregated WorkspaceDetails
     */
    fun details(
        projectId: String,
        branch: String,
        version: String,
    ): Mono<WorkspaceDetails> {
        log.info("Fetching Details for $version")

        val loadMarkdownMap =
            loadMarkdowns(projectId, branch, version)
                .collectList()
                .defaultIfEmpty(emptyList())
        val listTemplateJsons =
            listTemplateJsons(
                projectId,
                branch,
                version,
            )

        val packageTemplateJsonMono =
            gl
                .fetchFileTextInSingleLine(
                    projectId,
                    branch,
                    "$version/input/templates/package/package.template.json",
                ).defaultIfEmpty("")
        val metadataJsonMono =
            gl
                .fetchFileTextInSingleLine(
                    projectId,
                    branch,
                    "$version/metadata/metadata_package.json",
                ).defaultIfEmpty("")
        val changelogsJsonMono =
            gl
                .fetchFileTextInSingleLine(
                    projectId,
                    branch,
                    "$version/metadata/changelog.json",
                ).defaultIfEmpty("")
        val downloadConditionsMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/download-conditions.xml",
                ).defaultIfEmpty("")
        val externalSourcesMdMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/website/package/externalSources.md",
                ).defaultIfEmpty("")
        val fhirNotesMdMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/website/package/fhirConversionNotes.md",
                ).defaultIfEmpty("")
        val noteOnAuthorMdMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/website/package/noteOnAuthor.md",
                ).defaultIfEmpty("")
        val updateCyclesMdMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/website/package/notesOnUpdateCycles.md",
                ).defaultIfEmpty("")
        val descriptionGenericMdMono =
            gl
                .fetchFileTextLines(
                    projectId,
                    branch,
                    "$version/metadata/website/package/descriptionGeneric.md",
                ).defaultIfEmpty("")
        val inputFilesMono = fetchInputFiles(projectId, branch, version)
        val mrMono = fetchOpenMergeRequestRefOpt(projectId, branch)

        val left =
            Mono.zip(
                listTemplateJsons,
                loadMarkdownMap,
                packageTemplateJsonMono,
                metadataJsonMono,
                changelogsJsonMono,
                downloadConditionsMono,
                descriptionGenericMdMono,
            )

        val right =
            Mono.zip(
                externalSourcesMdMono,
                fhirNotesMdMono,
                noteOnAuthorMdMono,
                updateCyclesMdMono,
                inputFilesMono,
                mrMono,
            )

        return left.zipWith(right).map { leftRight ->
            val left = leftRight.t1
            val right = leftRight.t2
            WorkspaceDetails(
                templateJsons = left.t1,
                templateMds = left.t2,
                packageTemplateJson = left.t3,
                metadataJson = left.t4,
                changelogsJson = left.t5,
                downloadConditionsXml = left.t6,
                descriptionGenericMd = left.t7,
                externalSourcesMd = right.t1,
                fhirConversionNotesMd = right.t2,
                noteOnAuthorMd = right.t3,
                notesOnUpdateCyclesMd = right.t4,
                inputFileList = right.t5,
                mergeRequestRef = right.t6.orElse(null),
            )
        }
    }

    private fun repoTree(
        projectId: String,
        ref: String,
        path: String,
    ): Flux<GitLabTreeItemDto> =
        gl.getFlux(clazz = GitLabTreeItemDto::class.java) { uri ->
            uri
                .pathSegment("projects", projectId, "repository", "tree")
                .queryParam("ref", ref)
                .queryParam("path", path)
                .queryParam("per_page", 2000)
        }

    private fun listTemplateJsons(
        projectId: String,
        ref: String,
        base: String,
    ): Mono<List<TemplateJsonItem>> {
        val templatesRoot = "$base/input/templates"

        return repoTree(projectId, ref, templatesRoot)
            .filter { it.type == "blob" && it.path != null }
            .filter { it.path!!.endsWith(".json") }
            .flatMap({ item ->
                gl
                    .fetchFileTextInSingleLine(projectId, ref, item.path!!)
                    .defaultIfEmpty("")
                    .map { json -> TemplateJsonItem(name = item.name, json = json) }
            }, /* concurrency */ 4)
            .collectList()
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    Mono.just(emptyList())
                } else {
                    Mono.error(ex)
                }
            }
    }

    private fun lastModifiedForPath(
        projectId: String,
        ref: String,
        filePath: String,
    ): Mono<java.time.OffsetDateTime> =
        gl
            .getFlux(clazz = GitLabCommitListItemDto::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "repository", "commits")
                    .queryParam("ref_name", ref)
                    .queryParam("path", filePath)
                    .queryParam("per_page", 1)
            }.next()
            .map { c ->
                val s = c.committedDate
                if (s.isNullOrBlank()) java.time.OffsetDateTime.MIN else java.time.OffsetDateTime.parse(s)
            }.defaultIfEmpty(java.time.OffsetDateTime.MIN)
            .onErrorResume { Mono.just(java.time.OffsetDateTime.MIN) }

    private fun fetchInputFiles(
        projectId: String,
        ref: String,
        version: String,
    ): Mono<List<InputFile>> {
        val inputRoot = "$version/input"

        return repoTree(projectId, ref, inputRoot)
            .filter { it.type == "blob" && it.path != null }
            .filter { it.path!!.startsWith("$inputRoot/") }
            .flatMap({ item ->
                lastModifiedForPath(projectId, ref, item.path!!)
                    .map { lm ->
                        InputFile(name = item.name, lastModified = lm)
                    }
            }, /* concurrency */ 4)
            .collectList()
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    Mono.just(emptyList())
                } else {
                    Mono.error(ex)
                }
            }
    }

    private fun loadMarkdowns(
        projectId: String,
        ref: String,
        base: String,
    ): Flux<TemplateMdItem> {
        val indexPath = "$base/metadata/website/resources-description/index.json"

        return gl
            .fetchFileTextInSingleLine(projectId, ref, indexPath)
            .switchIfEmpty(Mono.just(""))
            .flatMapMany { raw ->
                if (raw.isBlank()) {
                    return@flatMapMany Flux.empty()
                }

                val idx: ResourceIndex =
                    try {
                        mapper.readValue(raw, ResourceIndex::class.java)
                    } catch (_: Exception) {
                        ResourceIndex()
                    }
                log.info(idx.toString())
                Flux
                    .fromIterable(idx.items)
                    .flatMap({ entry ->
                        val mdPath =
                            "$base/metadata/website/resources-description/${entry.markdownFile}"
                        gl
                            .fetchFileTextLines(projectId, ref, mdPath)
                            .defaultIfEmpty("")
                            .map { md -> TemplateMdItem(entry.url, md, entry.version) }
                    }, /* concurrency */ 4)
            }.onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    Flux.empty()
                } else {
                    Flux.error(ex)
                }
            }
    }

    private fun fetchOpenMergeRequestRefOpt(
        projectId: String,
        branch: String,
    ): Mono<Optional<MergeRequestRef>> =
        gl
            .getFlux(clazz = GitLabMergeRequest::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "merge_requests")
                    .queryParam("source_branch", branch)
                    .queryParam("state", "opened")
                    .queryParam("per_page", 1)
            }.next()
            .map { mr ->
                Optional.of(MergeRequestRef(mr.iid, mr.webUrl))
            }.defaultIfEmpty(Optional.empty())
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    Mono.just(Optional.empty())
                } else {
                    Mono.error(ex)
                }
            }

    private data class GitLabFileMetaDto(
        val size: Long?,
    )

    private data class GitLabCommitListItemDto(
        @param:JsonProperty("committed_date") val committedDate: String?,
    )

    private data class GitLabMergeRequest(
        val iid: Int,
        @param:JsonProperty("web_url") val webUrl: String,
        @param:JsonProperty("source_branch") val sourceBranch: String,
        val state: String,
    )
}
