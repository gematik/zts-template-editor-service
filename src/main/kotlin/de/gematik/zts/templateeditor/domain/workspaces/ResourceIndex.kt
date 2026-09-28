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

/**
 * Represents an index of resources, mapping URLs to their corresponding markdown files.
 * Used for mapping markdown file to canonical URL.
 */
data class ResourceIndex(
    @param:JsonProperty("resource-index") val items: MutableList<ResourceIndexEntry> = mutableListOf(),
)

data class ResourceIndexEntry(
    val url: String,
    val version: String,
    @param:JsonProperty("markdown-file") val markdownFile: String,
)
