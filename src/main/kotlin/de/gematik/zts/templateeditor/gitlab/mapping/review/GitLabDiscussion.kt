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

package de.gematik.zts.templateeditor.gitlab.mapping.review

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

/**
 * Represents a GitLab discussion attached to a merge request.
 *
 * A discussion groups one or more notes (comments) and may be marked as resolved.
 */
data class GitLabDiscussion(
    @param:JsonProperty("id")
    val id: String,
    @param:JsonProperty("notes")
    val notes: List<GitLabNote>,
    @param:JsonProperty("resolved")
    val resolved: Boolean? = null,
)

/**
 * Represents a single note (comment) within a GitLab discussion.
 */
data class GitLabNote(
    @param:JsonProperty("id")
    val id: Long,
    @param:JsonProperty("body")
    val body: String,
    @param:JsonProperty("author")
    val author: GitLabUser? = null,
    @param:JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    @param:JsonProperty("resolved")
    val resolved: Boolean? = null,
    @param:JsonProperty("position")
    val position: GitLabPosition? = null,
    @param:JsonProperty("system")
    val system: Boolean? = null,
)

/**
 * Describes the position of an inline note within a merge request diff.
 */
data class GitLabPosition(
    @param:JsonProperty("position_type")
    val positionType: String? = null,
    @param:JsonProperty("base_sha")
    val baseSha: String? = null,
    @param:JsonProperty("start_sha")
    val startSha: String? = null,
    @param:JsonProperty("head_sha")
    val headSha: String? = null,
    @param:JsonProperty("new_path")
    val newPath: String? = null,
    @param:JsonProperty("new_line")
    val newLine: Int? = null,
    @param:JsonProperty("old_path")
    val oldPath: String? = null,
    @param:JsonProperty("old_line")
    val oldLine: Int? = null,
)
