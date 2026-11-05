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

@Schema(description = "Pipeline that was created for the commit")
data class PipelineRef(
    @param:JsonProperty("id")
    @field:Schema(
        description = "Pipeline IID",
        example = "42",
    )
    val id: Int,
    @param:JsonProperty("status")
    @field:Schema(
        description = "Status of the latest pipeline",
        example = "success",
    )
    val status: String,
    @param:JsonProperty("webUrl")
    @field:Schema(
        description = "GitLab web URL of the latest pipeline",
        example = "https://gitlab.example.com/group/project/-/pipelines/latest",
    )
    val webUrl: String,
    @param:JsonProperty("updatedAt")
    @field:Schema(
        description = "The time at which the pipeline was updated",
        example = "2022-09-21T01:05:50.175Z",
    )
    val updatedAt: String,
    @param:JsonProperty("finishedAt")
    @field:Schema(
        description = "The time at which the pipeline was completed; if the field is missing, then null",
        example = "2022-09-21T01:05:50.175Z",
    )
    val finishedAt: String? = null,
)
