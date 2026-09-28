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

import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import de.gematik.zts.templateeditor.gitlab.ProjectsAdapter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/projects")
@Validated
@Tag(
    name = "Projects",
    description = "Lists GitLab projects the current authenticated user has access to.",
)
class ProjectsController(
    private val projectsAdapter: ProjectsAdapter,
) : ProjectsApi {
    @GetMapping
    override fun listProjects(
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "lastModified") sort: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): Flux<ProjectItem> {
        val sortKey =
            when (sort) {
                "lastModified" -> SortKey.LAST_CHANGE
                "title" -> SortKey.TITLE
                else -> throw IllegalArgumentException("invalid sort") // unreachable due to @RequestParam validation, but just in case
            }

        return projectsAdapter.list(search = search, sort = sortKey, page = page, size = pageSize)
    }

    enum class SortKey { LAST_CHANGE, TITLE }
}
