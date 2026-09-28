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

package de.gematik.zts.templateeditor.web.projects

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Flux

interface ProjectsApi {
    @Operation(
        summary = "List GitLab projects",
        description = "Returns a paginated list of GitLab projects, optionally searchable and sortable.",
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(name = "search", description = "Filters projects by substring match in name", required = false, example = "template"),
            Parameter(
                name = "sort",
                description = "Sort order of projects",
                required = false,
                example = "lastModified",
                schema = Schema(allowableValues = ["lastModified", "title"]),
            ),
            Parameter(name = "page", description = "1-based page index", required = false, example = "1"),
            Parameter(name = "pageSize", description = "Number of items per page (1–200)", required = false, example = "50"),
        ],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Projects successfully listed",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = ProjectItem::class)),
                    ),
                ],
            ),
        ],
    )
    fun listProjects(
        @RequestParam(required = false)
        @Length(max = 100)
        @Pattern(regexp = "^[\\p{L}0-9 _\\-.]*$", message = "search contains illegal characters")
        search: String?,
        @RequestParam(defaultValue = "lastModified")
        @Pattern(regexp = "^(lastModified|title)$", message = "sort must be one of: lastModified,title")
        sort: String,
        @RequestParam(defaultValue = "1")
        @Min(1)
        @Max(10_000)
        page: Int,
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(200)
        pageSize: Int,
    ): Flux<ProjectItem>
}
