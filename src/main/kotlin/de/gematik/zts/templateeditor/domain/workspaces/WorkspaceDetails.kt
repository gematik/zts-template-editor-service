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
import com.fasterxml.jackson.annotation.JsonRawValue
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Workspace content (templates, metadata, changelog, and editor notes) for a terminology package in a branch",
)
data class WorkspaceDetails(
    @param:JsonProperty("templatesJson")
    @field:Schema(
        description = "List of template files in the workspace (input/templates/*.template.json)",
    )
    val templateJsons: List<TemplateJsonItem>,
    @param:JsonProperty("templatesMd")
    @field:Schema(
        description = "List of template markdowns metadata/website/resources-description/*.md via index.json",
    )
    val templateMds: List<TemplateMdItem>,
    @param:JsonProperty("packageTemplateJson")
    @get:JsonRawValue
    @field:Schema(
        description = "Content of package.template.json (input/templates/package/package.template.json)",
        example = "{ \"url\": \"...\", ... }",
    )
    val packageTemplateJson: String,
    @param:JsonProperty("metadatenJson")
    @get:JsonRawValue
    @field:Schema(
        description = "Content of metadata_package.json (metadata/metadata_package.json)",
    )
    val metadataJson: String,
    @param:JsonProperty("changelogsJson")
    @get:JsonRawValue
    @field:Schema(
        description = "Content of changelog.json (metadata/changelog.json)",
    )
    val changelogsJson: String,
    @param:JsonProperty("externalSourcesMd")
    @field:Schema(
        description = "Markdown content for externalSources.md (metadata/website/package/externalSources.md)",
    )
    val externalSourcesMd: String,
    @param:JsonProperty("fhirConversionNotesMd")
    @field:Schema(
        description = "Markdown content for fhirConversionNotes.md (metadata/website/package/fhirConversionNotes.md)",
    )
    val fhirConversionNotesMd: String,
    @param:JsonProperty("noteOnAuthorMd")
    @field:Schema(
        description = "Markdown content for noteOnAuthor.md (metadata/website/package/noteOnAuthor.md)",
    )
    val noteOnAuthorMd: String,
    @param:JsonProperty("notesOnUpdateCyclesMd")
    @field:Schema(
        description = "Markdown content for notesOnUpdateCycles.md (metadata/website/package/notesOnUpdateCycles.md)",
    )
    val notesOnUpdateCyclesMd: String,
    @param:JsonProperty("downloadConditionsXml")
    @field:Schema(
        description = "Markdown content for descriptionGeneric.md (metadata/download-conditions.md",
    )
    val downloadConditionsXml: String,
    @param:JsonProperty("descriptionGenericMd")
    @field:Schema(
        description = "Markdown content for descriptionGeneric.md (metadata/website/package/descriptionGeneric.md)",
    )
    val descriptionGenericMd: String,
    @param:JsonProperty("inputFiles")
    @field:Schema(
        description = "Input TFC files (input/*)",
    )
    val inputFileList: List<InputFile>,
    @param:JsonProperty("mergeRequest")
    @field:Schema(
        description = "Associated merge request, if the workspace is linked to one",
        nullable = true,
    )
    val mergeRequestRef: MergeRequestRef? = null,
)
