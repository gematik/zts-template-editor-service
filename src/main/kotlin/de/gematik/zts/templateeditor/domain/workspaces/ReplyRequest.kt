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
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@Schema(description = "Request zum Antworten auf eine bestehende GitLab Discussion (Thread).")
data class ReplyRequest(
    @field:Schema(description = "GitLab Projekt-ID (Repository).", example = "12345")
    @param:JsonProperty("repositoryId")
    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
    @field:Length(max = 20)
    val repo: String,
    @field:Schema(description = "Merge Request IID.", example = "17")
    @param:JsonProperty("mrId")
    @field:Min(1)
    val mrId: Int,
    @field:Schema(
        description = "ID der GitLab Discussion (Thread).",
        example = "61568f79f53237eb15af304384ef50fa8d3d8ee9",
    )
    @param:JsonProperty("threadId")
    @field:NotBlank
    @field:Length(max = 100)
    @field:Pattern(
        regexp = "^[0-9a-fA-F]+$",
        message = "threadId must be a hex string matching GitLab discussion_id format",
    )
    val threadId: String,
    @field:Schema(description = "Text der Antwort (GitLab Note).", example = "Erledigt, habe ich angepasst.")
    @param:JsonProperty("body")
    @field:NotBlank
    @field:Length(max = 5000)
    @field:Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "body contains control characters")
    val body: String,
    @field:Schema(description = "Wenn true, wird der Thread nach dem Antworten als resolved markiert.", example = "true")
    @param:JsonProperty("resolved")
    val resolved: Boolean,
)
