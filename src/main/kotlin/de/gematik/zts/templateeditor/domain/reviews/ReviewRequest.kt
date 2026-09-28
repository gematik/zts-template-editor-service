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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

data class ReviewRequest(
    @param:JsonProperty("repositoryId")
    val repo: String,
    @param:JsonProperty("branch")
    val branch: String,
    @param:JsonProperty("target")
    val target: String? = null,
    @param:JsonProperty("title")
    val title: String? = null,
    @param:JsonProperty("description")
    val description: String? = null,
    @param:JsonProperty("labels")
    val labels: List<String>? = null,
    @param:JsonProperty("reviewers")
    val reviewers: List<ReviewerId>? = null,
    @param:JsonProperty("ready")
    val ready: Boolean = true,
)

sealed interface ReviewerId {
    @JsonValue
    fun json(): Any

    @JvmInline
    value class Username(
        val value: String,
    ) : ReviewerId {
        override fun json(): Any = value
    }

    @JvmInline
    value class UserId(
        val value: Int,
    ) : ReviewerId {
        override fun json(): Any = value
    }

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: Any): ReviewerId =
            when (value) {
                is String -> Username(value)
                is Int -> UserId(value)
                is Number -> UserId(value.toInt())
                else -> throw IllegalArgumentException("Invalid reviewer id: $value")
            }
    }
}
