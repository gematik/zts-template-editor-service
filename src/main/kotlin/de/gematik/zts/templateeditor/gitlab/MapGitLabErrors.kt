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

import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabBadRequestException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabConflictException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabForbiddenException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabNotFoundException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabUnauthorizedException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabUnhandledClientException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabServerErrorException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabServiceUnavailableException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabUpstreamException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Maps GitLab HTTP errors to stable API exceptions (no upstream details).
 *
 * Use this at the boundary where you call GitLab (Adapters/Services).
 */
fun <T : Any> Mono<T>.mapGitLabErrors(): Mono<T> = onErrorMap { ex -> mapGitLabErrorsLogic(ex) }

/**
 * Maps GitLab HTTP errors to stable API exceptions (no upstream details).
 *
 * Use this at the boundary where you call GitLab (Adapters/Services).
 */
fun <T : Any> Flux<T>.mapGitLabErrors(): Flux<T> =
    onErrorMap { ex ->
        mapGitLabErrorsLogic(ex)
    }

fun mapGitLabErrorsLogic(ex: Throwable): Throwable {
    val status =
        // In some cases GitLab returns 400 if a item already exists (e.g. create-operation) this should be handled as a 409
        if (
            (ex is GitLabHttpException && ex.message?.contains("already exists") == true && ex.status == 400) ||
            (ex is WebClientResponseException && ex.message.contains("already exists") && ex.statusCode.value() == 400)
        ) {
            409
        } else {
            when (ex) {
                is GitLabHttpException -> ex.status
                is WebClientResponseException -> ex.statusCode.value()
                else -> return ex
            }
        }

    return when (status) {
        400 -> GitLabBadRequestException()

        401 -> GitLabUnauthorizedException()

        403 -> GitLabForbiddenException()

        404 -> GitLabNotFoundException()

        409 -> GitLabConflictException()

//              405,409,412,422,429
        500 -> GitLabServerErrorException()

        503 -> GitLabServiceUnavailableException()

        in 400..499 -> GitLabUnhandledClientException(status)

        in 500..599 -> GitLabUpstreamException(status)

        else -> GitLabUpstreamException(status, "Gitlab returned unhandled upstream error with statuscode $status ")
    }
}
