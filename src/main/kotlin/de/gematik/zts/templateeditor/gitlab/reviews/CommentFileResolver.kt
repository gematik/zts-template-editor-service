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

import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.resolvePath
import org.springframework.stereotype.Component

@Component
class CommentFileResolver {
    fun resolveFromGitLabPath(
        fullPath: String,
        version: String,
        index: ResourceIndexLookup,
    ): Pair<CommitChange.Type, String> {
        val rel = fullPath.removePrefix("$version/")

        return when {
            rel.startsWith("input/templates/") &&
                rel.endsWith(".template.json") &&
                !rel.startsWith("input/templates/package/") -> {
                CommitChange.Type.TEMPLATE to rel.removePrefix("input/templates/")
            }

            rel == "input/templates/package/package.template.json" -> {
                CommitChange.Type.PACKAGE_TEMPLATE to "package.template.json"
            }

            rel == "metadata/metadata_package.json" -> {
                CommitChange.Type.METADATA to "metadata_package.json"
            }

            rel == "metadata/changelog.json" -> {
                CommitChange.Type.CHANGELOGS to "changelog.json"
            }

            rel == "metadata/download-conditions.xml" -> {
                CommitChange.Type.DOWNLOAD_CONDITIONS to "download-conditions.xml"
            }

            rel.startsWith("input/") && rel.count { c -> c == '/' } == 1 -> {
                CommitChange.Type.INPUT_FILE to rel.removePrefix("input/")
            }

            rel.startsWith("metadata/website/package/") -> {
                CommitChange.Type.PACKAGE_MARKDOWN to
                    rel.removePrefix("metadata/website/package/")
            }

            rel.startsWith("metadata/website/resources-description/") &&
                rel.endsWith(".md") -> {
                val md = rel.removePrefix("metadata/website/resources-description/")
                val url = index.urlForMarkdown(md) ?: md
                CommitChange.Type.TEMPLATE_MARKDOWN to url
            }

            rel == "metadata/website/resources-description/index.json" -> {
                CommitChange.Type.UPDATE_INDEX_URL_VERSION to "index.json"
            }

            else -> {
                CommitChange.Type.TEMPLATE to fullPath.substringAfterLast("/")
            }
        }
    }

    fun resolveGitLabPath(
        fileName: String,
        type: CommitChange.Type,
        version: String,
        index: ResourceIndexLookup,
    ): String =
        when (type) {
            CommitChange.Type.TEMPLATE_MARKDOWN -> {
                require(fileName.contains(";")) {
                    "Invalid fileName for TEMPLATE_MARKDOWN change: $fileName"
                }
                val versionUrl = fileName.split(";")

                val md = index.markdownForUrl(versionUrl[1], versionUrl[0]) ?: fileName
                "$version/metadata/website/resources-description/$md"
            }

            else -> {
                type.resolvePath(fileName, version)
            }
        }
}

interface ResourceIndexLookup {
    fun urlForMarkdown(md: String): String?

    fun markdownForUrl(
        url: String,
        version: String,
    ): String?
}
