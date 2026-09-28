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

package de.gematik.zts.templateeditor.domain.projects

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "A GitLab project entry")
data class ProjectItem(
    @param:JsonProperty("projectId")
    @param:Schema(description = "Internal GitLab project ID", example = "1278")
    val projectId: Long,
    @param:JsonProperty("title")
    @param:Schema(description = "Project title ", example = "OPS Terminologies for BfArM")
    val title: String,
    @param:JsonProperty("lastModified")
    @param:Schema(description = "Timestamp of last change", example = "2024-10-31T14:23:00Z")
    val lastModified: OffsetDateTime,
    @param:JsonProperty("description")
    @param:Schema(description = "Description of GitLab repository", example = "Example Project")
    val description: String,
)
