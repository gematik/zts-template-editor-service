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

package de.gematik.zts.templateeditor.domain.workspaces

import reactor.core.publisher.Mono

/**
 * Abstraction for retrieving merge request information for a repository branch.
 *
 * Implementations typically query the underlying GitLab provider
 * to determine whether an open merge request exists for a specific branch.
 *
 * This interface is used by authorization and workflow components that
 * require knowledge about active merge requests without depending on a
 * concrete GitLab implementation.
 */
interface MergeRequestLookup {
    /**
     * Finds an open merge request for the given project and source branch.
     *
     * If an open merge request exists, the method emits a [MergeRequestRef]
     * containing its identifier and web URL. If no open merge request exists,
     * the returned [Mono] completes empty.
     *
     * Implementations should treat "not found" responses from the upstream
     * system as an empty result rather than an error.
     *
     * @param projectId identifier of the project/repository
     * @param branch source branch of the merge request
     * @return a [Mono] emitting the open [MergeRequestRef], or completing empty
     *         if no open merge request exists
     */
    fun findOpenForBranch(
        projectId: String,
        branch: String,
    ): Mono<MergeRequestRef>
}
