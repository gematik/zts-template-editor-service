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

package de.gematik.zts.templateeditor.domain.reviews

import com.fasterxml.jackson.annotation.JsonProperty
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import java.time.OffsetDateTime

/**
 * Represents a shortend comment on a merge request on gitlab.
 */
data class CommentItem(
    @param:JsonProperty("id")
    val id: String,
    @param:JsonProperty("type")
    val type: CommitChange.Type? = null,
    @param:JsonProperty("fileName")
    val fileName: String? = null,
    @param:JsonProperty("line")
    val line: Int? = null,
    @param:JsonProperty("body")
    val body: String,
    @param:JsonProperty("author")
    val author: String? = null,
    @param:JsonProperty("createdAt")
    val createdAt: OffsetDateTime,
    @param:JsonProperty("resolved")
    val resolved: Boolean = false,
    @param:JsonProperty("threadId")
    val threadId: String? = null,
    @param:JsonProperty("isInline")
    val isInline: Boolean = false,
)
