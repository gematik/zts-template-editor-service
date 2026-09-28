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

package de.gematik.zts.templateeditor.domain.auth

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Details about the currently authenticated GitLab user",
)
data class UserDetailsSub(
    @param:Schema(
        description = "OIDC subject identifier of the authenticated user",
        example = "a1b2c3d4e5f6",
    )
    val name: String,
    @param:Schema(
        description = "Display name or nickname of the user",
        example = "Max",
    )
    val email: String,
    @param:Schema(
        description = "Indicates whether the email address is verified",
        example = "true",
    )
    @param:JsonProperty("email_verified")
    val emailVerified: Boolean,
    @param:Schema(
        description = "URL of the user's profile page",
        example = "https://gitlab.com/max.mustermann",
    )
    val groups: List<String> = emptyList(),
    @param:Schema(
        description = "True if the user is member of the GitLab reviewers group (can approve MRs)",
        example = "true",
    )
    @param:JsonProperty("is_reviewer")
    val isReviewer: Boolean = false,
)
