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

package de.gematik.zts.templateeditor.gitlab.mapping.project

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import java.time.OffsetDateTime

/**
 * Data transfer object representing a GitLab project.
 *
 * @param id The unique identifier of the project.
 * @param name The name of the project.
 * @param nameWithNamespace The full name of the project including its namespace.
 * @param path The path of the project.
 * @param lastActivityAt The timestamp of the last activity in the project.
 * @param defaultBranch The default branch of the project.
 * @param description The description of the project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitLabProjectDto(
    val id: Long,
    val name: String,
    @param:JsonProperty("name_with_namespace")
    val nameWithNamespace: String?,
    val path: String,
    @param:JsonProperty("last_activity_at")
    val lastActivityAt: String,
    @param:JsonProperty("default_branch")
    val defaultBranch: String?,
    val description: String?,
)

fun GitLabProjectDto.toProjectItem(): ProjectItem =
    ProjectItem(
        projectId = id,
        title = name,
        lastModified = OffsetDateTime.parse(lastActivityAt),
        description = description.orEmpty(),
    )
