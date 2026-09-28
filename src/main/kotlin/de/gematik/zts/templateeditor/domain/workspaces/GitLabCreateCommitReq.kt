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

package de.gematik.zts.templateeditor.domain.workspaces

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Single file action for a GitLab commit request.
 *
 * Mirrors the GitLab Commit API action object.
 * Depending on the action type, content and encoding may be required or omitted.
 */
data class GitLabCommitAction(
    val action: String,
    @param:JsonProperty("file_path") val filePath: String,
    val content: String? = null,
    val encoding: String? = null,
)

/**
 * GitLab API request to create a commit on a branch.
 */
data class GitLabCreateCommitReq(
    val branch: String,
    @param:JsonProperty("commit_message") val commitMessage: String,
    val actions: List<GitLabCommitAction>,
)

/**
 * Partial GitLab commit response.
 */
data class GitLabCommitResp(
    val id: String,
    @param:JsonProperty("short_id") val shortId: String? = null,
)

/**
 * Partial GitLab project response.
 */
data class GitLabProjectResp(
    @param:JsonProperty("default_branch") val defaultBranch: String,
)

/**
 * Partial GitLab merge request response.
 */
data class GitLabMergeRequestResp(
    val id: Int,
    @param:JsonProperty("web_url") val webUrl: String,
)

/**
 * Converts a domain-level [CommitChange] into a GitLab commit action.
 *
 * - Maps CREATE / UPDATE / DELETE to GitLab action strings.
 * - Resolves the repository file path based on change type and version.
 * - Omits content and encoding for delete actions, as required by GitLab.
 */
fun CommitChange.toGitLabAction(version: String): GitLabCommitAction {
    val act =
        when (action) {
            CommitChange.Action.CREATE -> "create"
            CommitChange.Action.UPDATE -> "update"
            CommitChange.Action.DELETE -> "delete"
        }

    val path = type.resolvePath(fileName, version)

    val enc =
        when (encoding) {
            CommitChange.Encoding.TEXT -> "text"
            CommitChange.Encoding.BASE64 -> "base64"
        }

    val needsContent = act != "delete"

    return GitLabCommitAction(
        action = act,
        filePath = path,
        content = if (needsContent) (content ?: "") else null,
        encoding = enc,
    )
}

/**
 * Resolves the full repository file path for a given change type.
 *
 * The returned path always includes the version prefix.
 */
fun CommitChange.Type.resolvePath(
    fileName: String,
    version: String,
): String =
    when (this) {
        CommitChange.Type.TEMPLATE -> {
            "$version/input/templates/$fileName"
        }

        CommitChange.Type.TEMPLATE_MARKDOWN -> {
            "$version/metadata/website/resources-description/$fileName"
        }

        CommitChange.Type.PACKAGE_TEMPLATE -> {
            "$version/input/templates/package/package.template.json"
        }

        CommitChange.Type.METADATA -> {
            "$version/metadata/metadata_package.json"
        }

        CommitChange.Type.CHANGELOGS -> {
            "$version/metadata/changelog.json"
        }

        CommitChange.Type.PACKAGE_MARKDOWN -> {
            "$version/metadata/website/package/$fileName"
        }

        CommitChange.Type.UPDATE_INDEX_URL_VERSION -> {
            "$version/metadata/website/resources-description/index.json"
        }

        CommitChange.Type.DOWNLOAD_CONDITIONS -> {
            "$version/metadata/download-conditions.xml"
        }

        CommitChange.Type.INPUT_FILE -> {
            "$version/input/$fileName"
        }
    }
