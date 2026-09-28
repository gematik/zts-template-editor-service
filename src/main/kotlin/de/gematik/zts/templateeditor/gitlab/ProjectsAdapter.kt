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

package de.gematik.zts.templateeditor.gitlab

import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import de.gematik.zts.templateeditor.gitlab.mapping.project.GitLabProjectDto
import de.gematik.zts.templateeditor.gitlab.mapping.project.toProjectItem
import de.gematik.zts.templateeditor.web.projects.ProjectsController.SortKey
import org.springframework.stereotype.Component
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux

/**
 * Adapter that retrieves project information from GitLab.
 *
 * Starting from the configured top-level group, this adapter fetches all
 * projects recursively across subgroups. After retrieval, projects are filtered:
 *
 * - Only projects whose names contain **1 or more dots** are included.
 * - Projects whose paths start with **"ctl"** (case-insensitive) are excluded.
 *
 * The resulting projects are mapped into the internal `ProjectItem` domain model.
 */
@Component
class ProjectsAdapter(
    private val gl: GitLabClient,
    private val properties: GitLabProperties,
) {
    /**
     * Lists filtered GitLab repositories as `ProjectItem`s.
     *
     * @param search Optional search term to filter projects by name.
     * @param sort Determines sorting behavior (by last change or by title).
     * @param page Page index for paginated results.
     * @param size Number of items per page.
     *
     * @return A reactive stream (`Flux`) of domain project items that:
     *   - reside inside the configured group (including subgroups),
     *   - do **not** belong to any `ctl` group,
     *   - and have **2 or 3 dots** in their project name.
     */
    fun list(
        search: String?,
        sort: SortKey,
        page: Int,
        size: Int,
    ): Flux<ProjectItem> {
        val orderBy =
            when (sort) {
                SortKey.LAST_CHANGE -> "last_activity_at"
                SortKey.TITLE -> "name"
            }

        return gl
            .getFlux(clazz = GitLabProjectDto::class.java) { uri ->
                uri.buildGitLabProjectsUri(properties, orderBy, size, page, search)
            }.filter { hasValidDotCount(it.name) }
            .filter { isNotCtl(it.path) }
            .map { it.toProjectItem() }
    }

    private fun UriBuilder.buildGitLabProjectsUri(
        props: GitLabProperties,
        orderBy: String,
        size: Int,
        page: Int,
        search: String?,
    ) = apply {
        pathSegment("groups", props.groupPath, "projects")
        queryParam("include_subgroups", "true")
        queryParam("simple", "true")
        queryParam("order_by", orderBy)
        queryParam("sort", "desc")
        queryParam("per_page", size)
        queryParam("page", page)
        search?.takeUnless { it.isBlank() }?.let { queryParam("search", it) }
    }

    private fun hasValidDotCount(name: String): Boolean {
        val dots = name.count { it == '.' }
        return dots >= 1
    }

    private fun isNotCtl(path: String) = !path.startsWith("ctl", ignoreCase = true)
}
