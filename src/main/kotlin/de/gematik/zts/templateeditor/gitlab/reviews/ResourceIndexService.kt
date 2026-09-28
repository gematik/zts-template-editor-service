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

package de.gematik.zts.templateeditor.gitlab.reviews

import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Service
class ResourceIndexService(
    private val gl: GitLabClient,
    private val mapper: ObjectMapper,
) {
    fun load(
        projectId: String,
        branch: String,
        version: String,
    ): Mono<ResourceIndex> =
        gl
            .fetchFileTextInSingleLine(
                projectId,
                branch,
                "$version/metadata/website/resources-description/index.json",
            ).map { raw ->
                runCatching {
                    mapper.readValue(raw, ResourceIndex::class.java)
                }.getOrElse { ResourceIndex(mutableListOf()) }
            }.switchIfEmpty(Mono.just(ResourceIndex(mutableListOf())))
            .onErrorResume(GitLabHttpException::class.java) { ex ->
                if (ex.message?.contains("404") == true) {
                    Mono.just(ResourceIndex(mutableListOf()))
                } else {
                    Mono.error(ex)
                }
            }.cache()
}
