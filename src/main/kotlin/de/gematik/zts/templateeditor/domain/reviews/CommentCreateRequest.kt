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

package de.gematik.zts.templateeditor.domain.reviews

import com.fasterxml.jackson.annotation.JsonProperty
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.gitlab.validation.ValidCommentFileName
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "Request zum Erstellen eines Merge-Request Inline-Kommentars (GitLab Discussion).")
@ValidCommentFileName
data class CommentCreateRequest(
    @field:Schema(description = "GitLab Projekt-ID (wird als `projects/{id}` verwendet).", example = "12345")
    @param:JsonProperty("repositoryId")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
    @field:Length(max = 20)
    val repositoryId: String,
    @field:Schema(description = "Branch, der zur ResourceIndex-Auflösung verwendet wird.", example = "feature/my-branch")
    @param:JsonProperty("branch")
    @field:NotBlank
    @field:Length(max = 255)
    @field:Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "branch contains illegal characters")
    val branch: String,
    @field:Schema(description = "Merge-Request IID als String.", example = "17")
    @param:JsonProperty("mrId")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$", message = "mrId must be numeric")
    @field:Length(max = 10)
    val mrId: String,
    @field:Schema(description = "Version-Prefix im Repo (z.B. Ordner `<version>/...`).", example = "1.2.3")
    @param:JsonProperty("version")
    @field:NotBlank
    @field:Length(max = 100)
    @field:Pattern(
        regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
        message = "version must be semver-like",
    )
    val version: String,
    @field:Schema(
        description = "Art der kommentierten Resource. Bestimmt, wie `fileName` in einen GitLab-Pfad gemappt wird.",
        example = "template",
    )
    @param:JsonProperty("type")
    @field:NotNull
    val type: CommitChange.Type,
    @field:Schema(description = "Identifier der Datei/Resource abhängig vom `type`.", example = "my.template.json")
    @param:JsonProperty("fileName")
    @field:NotBlank
    @field:Length(max = 300)
    val fileName: String,
    @field:Schema(description = "Zeilennummer in der Diff-Position. Muss gesetzt sein.", example = "42", nullable = true)
    @param:JsonProperty("line")
    @field:NotNull(message = "line must not be null")
    @field:Min(1)
    val line: Int? = null,
    @field:Schema(description = "Kommentartext für GitLab Discussion/Note.", example = "Bitte hier die Formulierung anpassen.")
    @param:JsonProperty("body")
    @field:NotBlank
    @field:Length(max = 5000)
    @field:Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "body contains control characters")
    val body: String,
)
