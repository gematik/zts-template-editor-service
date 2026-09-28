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
 * Response of the GitLab approve API.
 *
 * The response body is minimal and may only contain an ID depending
 * on the GitLab version and configuration.
 */
data class GitLabApproveResponse(
    val id: Long? = null,
)

/**
 * Detailed information about a GitLab merge request.
 *
 * This object is primarily used to retrieve commit SHAs required
 * for creating discussions and performing merge operations.
 */
data class GitLabMergeRequestDetails(
    @param:JsonProperty("sha")
    val sha: String,
    @param:JsonProperty("diff_refs")
    val diffRefs: DiffRefs,
) {
    data class DiffRefs(
        @param:JsonProperty("base_sha") val baseSha: String,
        @param:JsonProperty("start_sha") val startSha: String,
        @param:JsonProperty("head_sha") val headSha: String,
    )
}

/**
 * Response of the GitLab merge API.
 *
 * The response body is intentionally small and may only contain
 * an internal ID of the merge operation.
 */
data class GitLabMergeResponse(
    val id: Long? = null,
)
