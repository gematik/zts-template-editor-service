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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import de.gematik.zts.templateeditor.gitlab.validation.FileNameValidation
import de.gematik.zts.templateeditor.gitlab.validation.MaxContentLength
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length

@Schema(description = "Single file change that will be part of a workspace commit")
data class CommitChange(
    @param:JsonProperty("action")
    @field:NotNull
    @field:Schema(
        description = "Type of file operation",
        example = "update",
        allowableValues = ["create", "update", "delete"],
    )
    val action: Action,
    @param:JsonProperty("type")
    @field:NotNull
    @field:Schema(
        description = "Logical file type within the terminology package",
        example = "template",
        allowableValues = [
            "template",
            "template_markdown",
            "package_template",
            "metadata_package",
            "changelogs",
            "package_markdown",
            "update_index_url_version",
            "download_conditions",
            "input_file",
        ],
    )
    val type: Type,
    @param:JsonProperty("fileName")
    @field:NotBlank
    @field:Length(max = 300)
    @field:Schema(
        description = "File path relative to the package root",
        example = "input/templates/medication.template.json",
    )
    val fileName: String,
    @param:JsonProperty("content")
    @field:MaxContentLength
    @field:Schema(
        description = "File content. Required for create/update. Omitted for delete.",
        example = "{ \"resourceType\": \"Bundle\", ... }",
        nullable = true,
    )
    val content: String? = null,
    @param:JsonProperty("encoding")
    @field:NotNull
    @field:Schema(
        description = "Encoding of the content",
        example = "text",
        allowableValues = ["text", "base64"],
    )
    val encoding: Encoding = Encoding.TEXT,
) {
    @get:AssertTrue(message = "content must be provided for create/update and must be omitted for delete")
    val isContentValidForAction: Boolean
        get() =
            when (action) {
                Action.CREATE, Action.UPDATE -> true
                Action.DELETE -> content == null || content.isBlank()
            }

    @get:AssertTrue(message = "content must be valid base64 when encoding=base64")
    val isBase64ValidIfNeeded: Boolean
        get() {
            if (encoding != Encoding.BASE64) return true
            val c = content ?: return false
            if (c.length % 4 != 0) return false
            return c.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))
        }

    @get:AssertTrue(message = "INPUT_FILE must use encoding=base64 and fileName must be under input/*")
    val isFileValid: Boolean
        get() {
            if (type != Type.INPUT_FILE) return true
            if (encoding != Encoding.BASE64 && action != Action.DELETE) return false

            return fileName.count { it == '/' } == 0
        }

    @get:AssertTrue(message = "fileName contains illegal characters")
    val isFileNameValidForType: Boolean
        get() = FileNameValidation.isValid(type, fileName)

    enum class Action(
        @get:JsonValue val value: String,
    ) {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        ;

        companion object {
            @JvmStatic
            @JsonCreator
            fun from(value: String): Action =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("invalid action: $value")
        }
    }

    enum class Encoding(
        @get:JsonValue val value: String,
    ) {
        TEXT("text"),
        BASE64("base64"),
        ;

        companion object {
            @JvmStatic
            @JsonCreator
            fun from(value: String): Encoding =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("invalid encoding: $value")
        }
    }

    @Schema(
        description = "Typ der Änderung/Resource. Serialisiert als String gemäß `value`.",
    )
    enum class Type(
        @get:JsonValue val value: String,
    ) {
        @Schema(description = "Template unter `input/templates/` (außer package.template.json).", example = "template")
        TEMPLATE("template"),

        @Schema(
            description = "Markdown unter `metadata/website/resources-description/` (fileName ist `<version>;<url>`).",
            example = "template_markdown",
        )
        TEMPLATE_MARKDOWN("template_markdown"),

        @Schema(description = "Package Template: `input/templates/package/package.template.json`.", example = "package_template")
        PACKAGE_TEMPLATE("package_template"),

        @Schema(description = "Metadata Package: `metadata/metadata_package.json`.", example = "metadata_package")
        METADATA("metadata_package"),

        @Schema(description = "Changelog: `metadata/changelog.json`.", example = "changelogs")
        CHANGELOGS("changelogs"),

        @Schema(description = "Package Markdown unter `metadata/website/package/`.", example = "package_markdown")
        PACKAGE_MARKDOWN("package_markdown"),

        @Schema(
            description = "Update Index URL Version: `metadata/website/resources-description/index.json`.",
            example = "update_index_url_version",
        )
        UPDATE_INDEX_URL_VERSION("update_index_url_version"),

        @Schema(description = "download-conditions Markdown: `metadata/download-conditions.xml`.", example = "download_conditions")
        DOWNLOAD_CONDITIONS("download_conditions"),

        @Schema(description = "Input file unter `input/*` (binary, base64).", example = "input_file")
        INPUT_FILE("input_file"),
        ;

        companion object {
            @JvmStatic
            @JsonCreator
            fun from(value: String): Type =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("invalid type: $value")
        }
    }
}

@Schema(description = "Commit request for a workspace / branch in a repository")
data class CommitRequest(
    @param:JsonProperty("repositoryId")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
    @field:Length(max = 20)
    @field:Schema(
        description = "GitLab repository / project identifier",
        example = "1234",
    )
    val repositoryId: String,
    @param:JsonProperty("branch")
    @field:NotBlank
    @field:Length(max = 255)
    @field:Pattern(
        regexp = "^[A-Za-z0-9._/-]+$",
        message = "branch contains illegal characters",
    )
    @field:Schema(
        description = "Target branch to commit to",
        example = "feature/new-terminology",
    )
    val branch: String,
    @param:JsonProperty("version")
    @field:NotBlank
    @field:Length(max = 100)
    @field:Pattern(
        regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
        message = "version must be semver-like",
    )
    @field:Schema(
        description = "Terminology package version",
        example = "1.0.3",
    )
    val version: String,
    @param:JsonProperty("message")
    @field:NotBlank
    @field:Length(max = 200)
    @field:Pattern(
        regexp = "^[^\\p{Cntrl}]+$",
        message = "message contains control characters",
    )
    @field:Schema(
        description = "Commit message",
        example = "Update medication templates and changelog",
    )
    val message: String,
    @param:JsonProperty("changes")
    @field:Size(min = 1, max = 50)
    @field:Valid
    @field:Schema(
        description = "List of file changes to be applied in this commit",
    )
    val changes: List<CommitChange>,
    @param:JsonProperty("createMergeRequest")
    @field:Schema(
        description = "If true, a merge request will be created for this commit",
        example = "true",
    )
    val createMergeRequest: Boolean = false,
)
