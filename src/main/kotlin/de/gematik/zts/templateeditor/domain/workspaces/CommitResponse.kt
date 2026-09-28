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

@Schema(description = "Response after committing workspace changes")
data class CommitResponse(
    @param:JsonProperty("commitId")
    @field:Schema(
        description = "Git commit SHA",
        example = "5e8c1f0b3a9d4c2e7f1b0a...",
    )
    val commitId: String,
    @param:JsonProperty("head")
    @field:Schema(
        description = "Branch head after the commit (usually the same SHA as commitId)",
        example = "5e8c1f0b3a9d4c2e7f1b0a...",
    )
    val head: String,
    @param:JsonProperty("mergeRequest")
    @field:Schema(
        description = "Merge request created for this commit, if any",
        nullable = true,
    )
    val mergeRequest: MergeRequestRef? = null,
)
