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

import de.gematik.zts.templateeditor.domain.versions.VersionItem
import de.gematik.zts.templateeditor.gitlab.VersionsAdapter
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/projects/{projectId}/versions")
@Validated
class VersionsController(
    private val versionsAdapter: VersionsAdapter,
) : VersionsApi {
    @GetMapping
    override fun listVersions(
        @PathVariable projectId: String,
        @RequestParam(required = false) workspace: String?,
    ): Flux<VersionItem> = versionsAdapter.list(projectId = projectId, workspace = workspace)

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{version}")
    override fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable version: String,
        @RequestParam(required = false) workspace: String?,
    ): Mono<Void> = versionsAdapter.deleteVersion(projectId = projectId, workspace = workspace, version = version).then()
}
