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

package de.gematik.zts.templateeditor.web.workspaces.comments

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateRequest
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateResponse
import de.gematik.zts.templateeditor.domain.reviews.CommentsPayload
import de.gematik.zts.templateeditor.domain.workspaces.ReplyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Mono

@Tag(name = "Workspaces Comments", description = "Kommentare und Replies für Merge-Request Reviews")
interface WorkspacesCommentsApi {
    @Operation(
        summary = "Kommentare eines Merge Requests laden",
        description = "Liest alle nicht-System-Kommentare aus GitLab Discussions und mappt sie in das interne CommentsPayload.",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Kommentare erfolgreich geladen",
                content = [Content(schema = Schema(implementation = CommentsPayload::class))],
            ),
            ApiResponse(responseCode = "404", description = "Merge Request/Repository nicht gefunden"),
        ],
    )
    fun getComments(
        @Parameter(description = "GitLab Project/Repository ID", required = true, example = "12345")
        @RequestParam
        @Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
        @Length(max = 20)
        repositoryId: String,
        @Parameter(description = "Merge Request IID", required = true, example = "17")
        @RequestParam
        @Min(1)
        mrId: Int,
        @Parameter(description = "Branch-Name", required = true, example = "feature/my-branch")
        @RequestParam
        @Length(max = 255)
        @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "branch contains illegal characters")
        branch: String,
        @Parameter(description = "Version/Tag des Templates (für Mapping/Resolver)", required = true, example = "1.2.3")
        @RequestParam
        @Length(max = 100)
        @Pattern(
            regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
            message = "version must be semver-like",
        )
        version: String,
    ): Mono<CommentsPayload>

    @Operation(
        summary = "Inline-Kommentar erstellen",
        description = "Erstellt eine neue GitLab Discussion an einer Dateiposition (newPath + newLine).",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Kommentar erstellt",
                content = [Content(schema = Schema(implementation = CommentCreateResponse::class))],
            ),
            ApiResponse(responseCode = "404", description = "Merge Request/Datei nicht gefunden"),
        ],
    )
    fun createComment(
        @Valid @RequestBody req: CommentCreateRequest,
    ): Mono<ResponseEntity<CommentCreateResponse>>

    @Operation(
        summary = "Auf Thread antworten (und optional resolven)",
        description = "Erstellt eine Note in einer bestehenden Discussion. Wenn resolved=true, wird der Thread anschließend resolved.",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Reply erstellt",
                content = [Content(schema = Schema(implementation = CommentCreateResponse::class))],
            ),
            ApiResponse(responseCode = "404", description = "Thread/MR nicht gefunden"),
        ],
    )
    fun reply(
        @Valid @RequestBody req: ReplyRequest,
    ): Mono<ResponseEntity<CommentCreateResponse>>
}
