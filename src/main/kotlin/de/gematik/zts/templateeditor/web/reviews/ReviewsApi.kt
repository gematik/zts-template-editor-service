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

package de.gematik.zts.templateeditor.web.reviews

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.reviews.ReviewsOverviewItem
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "Reviews", description = "Merge-Request Reviews (Liste, Approve + Merge)")
interface ReviewsApi {

    @Operation(
        summary = "Merge Requests listen",
        description = "Listet Merge Requests aus GitLab (Gruppen-scope inkl. Subgroups)."
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Liste von Reviews",
                content = [Content(array = ArraySchema(schema = Schema(implementation = ReviewsOverviewItem::class)))]
            )
        ]
    )
    fun listReviews(
        @RequestParam(name = "state", defaultValue = "opened")
        @Pattern(
            regexp = "^(opened|merged|closed|all)$",
            message = "state must be one of: opened,merged,closed,all"
        )
        state: String,

        @RequestParam(name = "page", defaultValue = "1")
        @Min(1)
        @Max(10_000)
        page: Int,

        @RequestParam(name = "pageSize", defaultValue = "50")
        @Min(1)
        @Max(200)
        pageSize: Int
    ): Flux<ReviewsOverviewItem>

    @Operation(
        summary = "Approve und Auto-Merge triggern",
        description = "Führt zuerst Approve aus und triggert danach das Merge (should_remove_source_branch=true). Response ist aktuell ein String-Text."
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Approve + Merge wurde getriggert",
                content = [Content(schema = Schema(implementation = String::class))]
            ),
            ApiResponse(responseCode = "404", description = "Projekt oder MR nicht gefunden")
        ]
    )
    fun approveAndMergeMR(
        @RequestParam(name = "projectId")
        @Min(1)
        projectId: Long,

        @RequestParam(name = "mrId")
        @Min(1)
        mrId: Long
    ): Mono<String>
}
