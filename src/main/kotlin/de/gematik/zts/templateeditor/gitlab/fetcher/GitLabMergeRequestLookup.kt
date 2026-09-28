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

package de.gematik.zts.templateeditor.gitlab.fetcher

import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestLookup
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabNotFoundException
import de.gematik.zts.templateeditor.gitlab.mapGitLabErrors
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class GitLabMergeRequestLookup(
    private val gl: GitLabClient,
) : MergeRequestLookup {
    override fun findOpenForBranch(
        projectId: String,
        branch: String,
    ): Mono<MergeRequestRef> =
        gl
            .getFlux(clazz = GitLabMergeRequestDto::class.java) { uri ->
                uri
                    .pathSegment("projects", projectId, "merge_requests")
                    .queryParam("state", "opened")
                    .queryParam("source_branch", branch)
                    .queryParam("per_page", 1)
            }.mapGitLabErrors()
            .next()
            .map { mr -> MergeRequestRef(id = mr.iid, webUrl = mr.webUrl) }
            .onErrorResume(GitLabNotFoundException::class.java) { Mono.empty() }
}
