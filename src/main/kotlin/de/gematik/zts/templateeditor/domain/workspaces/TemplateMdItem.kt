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
 * Represents a template JSON item with its name and content.
 * @param url The name of the template file.
 * @param markdown The markdown content of the canonicalUrl as a string located in $VERSION/metadata/editor/resources-description/${MAPPED_WITH_RESOURCE_INDEX(canonicalUrl)}.
 * @param version The version of the template markdown. Can be different from the package version.
 */
data class TemplateMdItem(
    @param:JsonProperty("canonicalUrl")
    val url: String,
    @param:JsonProperty("markdown")
    val markdown: String,
    @param:JsonProperty("version")
    val version: String,
)
