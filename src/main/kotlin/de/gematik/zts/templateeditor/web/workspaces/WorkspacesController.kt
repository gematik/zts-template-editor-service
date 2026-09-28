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

import de.gematik.zts.templateeditor.domain.reviews.ReviewRequest
import de.gematik.zts.templateeditor.domain.workspaces.BranchItem
import de.gematik.zts.templateeditor.domain.workspaces.CommitRequest
import de.gematik.zts.templateeditor.domain.workspaces.CommitResponse
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.WorkspaceDetails
import de.gematik.zts.templateeditor.gitlab.WorkspacesAdapter
import jakarta.validation.Valid
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/workspaces")
@Validated
class WorkspacesController(
    private val workspacesAdapter: WorkspacesAdapter,
) : WorkspacesApi {
    @GetMapping
    override fun listBranches(
        @RequestParam repositoryId: String,
    ): Flux<BranchItem> = workspacesAdapter.listBranches(repositoryId)

    @GetMapping("/details")
    override fun details(
        @RequestParam repositoryId: String,
        @RequestParam branch: String,
        @RequestParam version: String,
    ): Mono<WorkspaceDetails> = workspacesAdapter.details(repositoryId, branch, version)

    @PostMapping("/commit")
    override fun commit(
        @Valid @RequestBody request: CommitRequest,
    ): Mono<CommitResponse> = workspacesAdapter.commit(request)

    @PostMapping("/commitFile", consumes = ["multipart/form-data"])
    override fun commitFile(
        @RequestPart("repositoryId") repositoryId: String,
        @RequestPart("branch") branch: String,
        @RequestPart("version") version: String,
        @RequestPart("message") message: String,
        @RequestPart("action") action: String,
        @RequestPart("file") file: FilePart,
        @RequestPart("fileName") fileName: String,
    ): Mono<CommitResponse> =
        workspacesAdapter.commitFile(
            repositoryId = repositoryId,
            branch = branch,
            version = version,
            message = message,
            action = action,
            file = file,
            fileName = fileName,
        )

    @PostMapping("/review")
    override fun review(
        @Valid @RequestBody request: ReviewRequest,
    ): Mono<MergeRequestRef> = workspacesAdapter.review(request)

    @GetMapping("/openByMR")
    override fun openByMr(
        @RequestParam projectId: String,
        @RequestParam mrId: Int,
        @RequestParam version: String,
    ): Mono<WorkspaceDetails> = workspacesAdapter.openByMr(projectId, mrId, version)
}
