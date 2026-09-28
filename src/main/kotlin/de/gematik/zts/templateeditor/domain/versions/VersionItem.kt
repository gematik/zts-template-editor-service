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

package de.gematik.zts.templateeditor.domain.versions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(
    description = "Represents a single version of a terminology resource in a given branch",
)
data class VersionItem(
    @param:JsonProperty("version")
    @field:Schema(
        description = "Version identifier of the terminology resource",
        example = "1.0.3",
    )
    val version: String,
    @param:JsonProperty("lastModified")
    @field:Schema(
        description = "Last modification timestamp of this version in the given branch",
        example = "2024-10-31T14:23:00+01:00",
    )
    val lastModified: OffsetDateTime,
)
