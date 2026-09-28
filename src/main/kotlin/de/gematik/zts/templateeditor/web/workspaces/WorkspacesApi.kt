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

package de.gematik.zts.templateeditor.web.workspaces

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.reviews.ReviewRequest
import de.gematik.zts.templateeditor.domain.workspaces.BranchItem
import de.gematik.zts.templateeditor.domain.workspaces.CommitRequest
import de.gematik.zts.templateeditor.domain.workspaces.CommitResponse
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.WorkspaceDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(
    name = "Workspaces",
    description = "Workspace / branch handling and committing changes for terminology packages",
)
interface WorkspacesApi {
    @Operation(
        summary = "List branches (workspaces) for a terminology package",
        description = "Returns all branches (workspaces) that contain the given package and version in the repository.",
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(name = "repositoryId", description = "GitLab repository / project identifier", required = true, example = "1234"),
        ],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Branches successfully listed",
                content = [
                    Content(
                        mediaType = "application/json",
                        array = ArraySchema(schema = Schema(implementation = BranchItem::class)),
                    ),
                ],
            ),
        ],
    )
    fun listBranches(
        @RequestParam
        @Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
        @Length(max = 20)
        repositoryId: String,
    ): Flux<BranchItem>

    @Operation(
        summary = "Get workspace details for a branch",
        description = "Returns all workspace files (templates, metadata, changelog, notes, …) for the given repository/branch/version.",
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(name = "repositoryId", description = "GitLab repository / project identifier", required = true, example = "1234"),
            Parameter(
                name = "branch",
                description = "Branch name (workspace) to open",
                required = true,
                example = "feature/new-terminology",
            ),
            Parameter(name = "version", description = "Terminology package version", required = true, example = "1.0.3"),
        ],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Workspace successfully loaded",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkspaceDetails::class))],
            ),
        ],
    )
    fun details(
        @RequestParam
        @Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
        @Length(max = 20)
        repositoryId: String,
        @RequestParam
        @Length(max = 255)
        @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "branch contains illegal characters")
        branch: String,
        @RequestParam
        @Length(max = 100)
        @Pattern(
            regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
            message = "version must be semver-like",
        )
        version: String,
    ): Mono<WorkspaceDetails>

    @Operation(
        summary = "Commit workspace changes",
        description = "Commits changes for a workspace (branch) using the given list of file changes. Optionally opens a merge request.",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Changes successfully committed",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CommitResponse::class))],
            ),
        ],
    )
    fun commit(
        @Valid @RequestBody request: CommitRequest,
    ): Mono<CommitResponse>

    @Operation(
        summary = "Start review for workspace changes",
        description = "Opens a merge request.",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Merge request successfully opened",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = MergeRequestRef::class))],
            ),
        ],
    )
    fun review(
        @Valid @RequestBody request: ReviewRequest,
    ): Mono<MergeRequestRef>

    @Operation(
        summary = "Upload File and commit it to a workspace",
        description =
            "Uploads a file as a single repository file and commits it to the given workspace (branch). " +
                "The file is stored as base64 in the commit request. Optionally opens a merge request.",
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(name = "repositoryId", description = "GitLab repository / project identifier", required = true, example = "1234"),
            Parameter(name = "branch", description = "Target branch to commit to", required = true, example = "feature/new-terminology"),
            Parameter(name = "version", description = "Terminology package version", required = true, example = "1.0.3"),
            Parameter(name = "message", description = "Commit message", required = true, example = "Upload workspace.zip"),
            Parameter(
                name = "action",
                description = "Commit action for the file",
                required = true,
                example = "update",
                schema = Schema(allowableValues = ["create", "update"]),
            ),
            Parameter(name = "fileName", description = "Target file name in repository", required = true, example = "workspace.zip"),
        ],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "File successfully committed",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CommitResponse::class))],
            ),
        ],
    )
    @org.springframework.web.bind.annotation.PostMapping(
        path = ["/workspaces/commitFile"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun commitFile(
        @RequestPart("repositoryId")
        @Pattern(regexp = "^[0-9]+$", message = "repositoryId must be numeric")
        @Length(max = 20)
        repositoryId: String,
        @RequestPart("branch")
        @Length(max = 255)
        @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "branch contains illegal characters")
        branch: String,
        @RequestPart("version")
        @Length(max = 100)
        @Pattern(
            regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
            message = "version must be semver-like",
        )
        version: String,
        @RequestPart("message")
        @Length(max = 200)
        @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "message contains control characters")
        message: String,
        @RequestPart("action")
        @Pattern(regexp = "^(create|update)$", message = "action must be create or update")
        action: String,
        @RequestPart("file")
        file: FilePart,
        @RequestPart("fileName")
        @Length(max = 300)
        @Pattern(
            regexp = "^(?!.*\\.\\.)(?!.*[\\\\/])[A-Za-z0-9._;-]+$",
            message = "fileName contains illegal characters",
        )
        fileName: String,
    ): Mono<CommitResponse>

    @Operation(
        summary = "Open workspace by merge request",
        description = "Loads workspace details for the branch associated with the given merge request.",
    )
    @BearerAuth
    @StandardApiErrors
    @Parameters(
        value = [
            Parameter(
                name = "projectId",
                description = "GitLab project identifier of the merge request",
                required = true,
                example = "1234",
            ),
            Parameter(name = "mrId", description = "Merge request IID (internal ID within the project)", required = true, example = "42"),
            Parameter(name = "version", description = "Terminology package version", required = true, example = "1.0.3"),
        ],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Workspace successfully loaded",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkspaceDetails::class))],
            ),
            ApiResponse(responseCode = "404", description = "Merge request or associated workspace not found"),
        ],
    )
    fun openByMr(
        @RequestParam
        @Pattern(regexp = "^[0-9]+$", message = "projectId must be numeric")
        @Length(max = 20)
        projectId: String,
        @RequestParam
        @Min(1)
        mrId: Int,
        @RequestParam
        @Length(max = 100)
        @Pattern(
            regexp = "^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$",
            message = "version must be semver-like",
        )
        version: String,
    ): Mono<WorkspaceDetails>
}
