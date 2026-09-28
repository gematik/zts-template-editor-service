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

import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.mapGitLabErrors
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class UserInfoService(
    private val gitLabClient: GitLabClient,
    private val gitLabProperties: GitLabProperties,
) {
    /**
     * Retrieves details about the currently authenticated user.
     */
    fun userInfo(): Mono<UserDetailsSub> =
        currentProxyDetails().flatMap { details ->
            val forwardedGroups = details.groups
            val baseUser = UserDetailsSub(
                name = details.user.orEmpty(),
                email = details.email.orEmpty(),
                emailVerified = details.email != null,
                groups = forwardedGroups,
            )

            val userInfo = if (forwardedGroups.isNotEmpty()) {
                Mono.just(baseUser)
            } else {
                gitLabClient
                    .getFlux(apiBase = gitLabClient.getUserInfoURI(), clazz = UserDetailsSub::class.java)
                    .mapGitLabErrors()
                    .next()
                    .defaultIfEmpty(baseUser)
            }

            userInfo.map(::markReviewer)
        }

    private fun currentProxyDetails(): Mono<AuthSessionDetails> =
        ReactiveSecurityContextHolder
            .getContext()
            .switchIfEmpty(Mono.error(IllegalStateException("no auth")))
            .mapNotNull { ctx -> ctx.authentication?.details as? AuthSessionDetails }
            .switchIfEmpty(Mono.error(IllegalStateException("no oauth2-proxy auth details")))

    private fun markReviewer(user: UserDetailsSub): UserDetailsSub {
        val reviewersKey = gitLabProperties.reviewersGroup.trim().lowercase()
        val isReviewer = user.groups.any { group ->
            val normalizedGroup = group.trim().lowercase()
            normalizedGroup == reviewersKey || normalizedGroup.endsWith("/$reviewersKey")
        }
        return user.copy(isReviewer = isReviewer)
    }
}
