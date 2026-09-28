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
import java.time.OffsetDateTime

@Schema(description = "Represents a workspace branch for a terminology package")
data class BranchItem(
    @param:JsonProperty("branch")
    @field:Schema(
        description = "Branch name (workspace name)",
        example = "feature/new-terminology",
    )
    val branch: String,
    @param:JsonProperty("lastModified")
    @field:Schema(
        description = "Timestamp of the last modification in this branch related to the package",
        example = "2024-10-31T14:23:00+01:00",
        nullable = true,
    )
    val lastModified: OffsetDateTime? = null,
    @param:JsonProperty("author")
    @field:Schema(
        description = "Author of the last relevant change",
        example = "max.mustermann",
        nullable = true,
    )
    val author: String? = null,
    @param:JsonProperty("isDefaultBranch")
    @field:Schema(
        description = "Boolean if branch is default branch",
        example = "true",
    )
    val default: Boolean = false,
    @param:JsonProperty("isProtected")
    @field:Schema(
        description = "Boolean if branch is protected branch",
        example = "true",
    )
    val protected: Boolean = false,
    @param:JsonProperty("mergeRequest")
    val mergeRequestRef: MergeRequestRef? = null,
)
