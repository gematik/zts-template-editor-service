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

package de.gematik.zts.templateeditor.gitlab.constants

import org.springframework.http.HttpStatus

/**
 * Represents specific error codes for interactions with the GitLab API.
 *
 * Each enum constant maps a domain-specific GitLab error to a corresponding
 * @HttpStatus. These error codes are used to handle HTTP error responses
 * in a structured way and translate them into meaningful application-level errors.
 *
 * @property status The associated @HttpStatus that corresponds to the GitLab error.
 */
enum class GitLabErrorCode(
    val status: HttpStatus,
) {
    // 400 Bad Request
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND),

    // 409 Conflict
    CONFLICT(HttpStatus.CONFLICT),

    // Unhandled 4xx
    UNHANDLED_CLIENT(HttpStatus.BAD_REQUEST),

    // 500 Internal Server Error
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    // 5xx Upstream/Other Server Errors
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY),
}
