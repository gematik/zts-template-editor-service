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

package de.gematik.zts.templateeditor.web.versions

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.versions.VersionItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "Versions", description = "List terminology versions for a given project and branch")
interface VersionsApi {

    @Operation(
        summary = "List terminology versions for a project",
        description = "Returns all known terminology versions for the given project. If `workspace` is not provided, the project's default branch is used."
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(
                name = "projectId",
                description = "GitLab project numeric identifier (ID)",
                required = true,
                example = "1234"
            ),
            Parameter(
                name = "workspace",
                description = "Branch name (workspace). If omitted, the default branch is used.",
                required = false,
                example = "feature/new-terminology"
            )
        ]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Versions successfully listed",
                content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = VersionItem::class)))]
            )
        ]
    )
    fun listVersions(
        @PathVariable
        @Length(max = 20)
        @Pattern(
            regexp = "^[0-9]+$",
            message = "projectId must be numeric ID"
        )
        projectId: String,

        @RequestParam(required = false)
        @Length(max = 255)
        @Pattern(
            regexp = "^[A-Za-z0-9._/-]+$",
            message = "workspace contains illegal characters"
        )
        workspace: String?
    ): Flux<VersionItem>

    @Operation(summary = "Delete a terminology version")
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deleted"),
            ApiResponse(responseCode = "404", description = "Version not found")
        ]
    )
    fun deleteVersion(
        @PathVariable
        @Length(max = 20)
        @Pattern(
            regexp = "^[0-9]+$",
            message = "projectId must be numeric ID"
        )
        projectId: String,

        @PathVariable
        @Length(max = 100)
        @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "version contains illegal characters"
        )
        version: String,

        @RequestParam(required = false)
        @Length(max = 255)
        @Pattern(
            regexp = "^[A-Za-z0-9._/-]+$",
            message = "workspace contains illegal characters"
        )
        workspace: String?
    ): Mono<Void>
}
