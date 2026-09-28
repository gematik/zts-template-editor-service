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

/**
 * Request payload for creating a new GitLab discussion on a merge request.
 *
 * This is used to create an inline comment bound to a specific
 * position in a merge request diff. Used in the GitLab API for
 * creating discussions.
 */
data class GitLabDiscussionCreateRequest(
    val body: String,
    val position: Position,
) {
    data class Position(
        @param:JsonProperty("position_type")
        val positionType: String, // "text"
        @param:JsonProperty("base_sha")
        val baseSha: String,
        @param:JsonProperty("start_sha")
        val startSha: String,
        @param:JsonProperty("head_sha")
        val headSha: String,
        @param:JsonProperty("new_path")
        val newPath: String? = null,
        @param:JsonProperty("new_line")
        val newLine: Int? = null,
        @param:JsonProperty("old_path")
        val oldPath: String? = null,
        @param:JsonProperty("old_line")
        val oldLine: Int? = null,
    )
}
