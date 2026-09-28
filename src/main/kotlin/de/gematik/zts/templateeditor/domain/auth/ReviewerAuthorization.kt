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

package de.gematik.zts.templateeditor.domain.auth

import de.gematik.zts.templateeditor.domain.exception.ForbiddenException
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestLookup
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Authorization helper for reviewer-specific access rules.
 *
 * This component centralizes permission checks related to reviewers and
 * merge request requirements. It is used by services and adapters before
 * executing operations that have reviewer-related restrictions.
 *
 * All methods return a reactive result that completes successfully when the
 * authorization check passes, or emits a [ForbiddenException] when access
 * is not allowed.
 *
 * Rules enforced by this component:
 * - Non-reviewers may perform normal workspace operations.
 * - Reviewers may only modify a branch if an open merge request exists.
 * - Certain operations are restricted exclusively to reviewers or
 *   non-reviewers.
 *
 * The methods are designed to be composed in reactive pipelines using
 * operators like `then(...)`.
 */
@Component
class ReviewerAuthorization(
    private val userInfoService: UserInfoService,
    private val mergeRequestLookup: MergeRequestLookup,
) {
    fun ensureReviewerState(required: Boolean): Mono<Unit> =
        userInfoService
            .userInfo()
            .filter { it.isReviewer == required }
            .switchIfEmpty(missingPermissionsErrorMono(required))
            .thenReturn(Unit)

    fun ensureReviewerHasOpenMrOrUnauthorized(
        projectId: String,
        branch: String,
    ): Mono<Unit> =
        userInfoService
            .userInfo()
            .flatMap { user ->
                if (!user.isReviewer) {
                    Mono.empty()
                } else {
                    mergeRequestLookup
                        .findOpenForBranch(projectId, branch)
                        .switchIfEmpty(
                            Mono.error(ForbiddenException("Reviewer requires open merge request")),
                        ).thenReturn(Unit)
                }
            }

    private fun missingPermissionsErrorMono(required: Boolean): Mono<UserDetailsSub> =
        Mono.error(
            ForbiddenException(
                if (required) {
                    "User must be reviewer"
                } else {
                    "Operation not allowed for reviewers"
                },
            ),
        )
}
