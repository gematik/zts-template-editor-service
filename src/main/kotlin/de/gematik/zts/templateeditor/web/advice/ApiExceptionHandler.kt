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

package de.gematik.zts.templateeditor.web.advice

import de.gematik.zts.templateeditor.domain.exception.ForbiddenException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.GitLabRuntimeException
import de.gematik.zts.templateeditor.logging.logger
import de.gematik.zts.templateeditor.web.constants.ApiErrorCode
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException

@RestControllerAdvice
class ApiExceptionHandler {
    private val log by logger()

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiError> {
        val details =
            ex.constraintViolations.map {
                FieldError(
                    field = it.propertyPath.toString(),
                    message = it.message,
                )
            }

        val root = ex.cause ?: ex

        val errorCode = ApiErrorCode.VALIDATION_FAILED

        log.warn(
            "Validation failed: {} - {}, violations={}",
            root::class.simpleName,
            root.message,
            details.joinToString(prefix = "[", postfix = "]") { "${it.field}: ${it.message}" },
        )

        return ResponseEntity
            .status(errorCode.status)
            .body(
                ApiError(
                    code = errorCode.name,
                    message = errorCode.toApiMessage,
                    details = details,
                ),
            )
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleBind(ex: WebExchangeBindException): ResponseEntity<ApiError> {
        val details =
            ex.fieldErrors.map {
                FieldError(field = it.field, message = it.defaultMessage ?: "invalid")
            }

        val errorCode = ApiErrorCode.VALIDATION_FAILED

        log.warn(
            "Bind validation failed: {} - {}, fields={}",
            ex::class.simpleName,
            ex.message,
            details.joinToString(prefix = "[", postfix = "]") { "${it.field}: ${it.message}" },
        )

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiError(code = errorCode.name, message = errorCode.toApiMessage, details = details))
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleInput(ex: ServerWebInputException): ResponseEntity<ApiError> {
        log.warn(
            "Invalid request input: {} - {}",
            ex::class.simpleName,
            ex.message,
        )

        val errorCode = ApiErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiError(code = errorCode.name, message = errorCode.toApiMessage))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        log.warn(
            "Illegal argument: {} - {}",
            ex::class.simpleName,
            ex.message,
        )

        val errorCode = ApiErrorCode.BAD_REQUEST

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiError(code = errorCode.name, message = errorCode.toApiMessage))
    }

    // GitLab-Api-Exception-Mapping
    @ExceptionHandler(GitLabRuntimeException::class)
    fun handleGitlabRuntimeException(ex: GitLabRuntimeException): ResponseEntity<ApiError> {
        log.warn(
            "GitLab API Error: {} - {}",
            ex::class.simpleName,
            ex.message,
        )

        return ResponseEntity
            .status(ex.gitLabErrorCode.status)
            .body(ApiError(code = ex.gitLabErrorCode.name, message = ex.toApiMessage()))
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<ApiError> {
        log.warn(
            "Forbidden access: {} - {}",
            ex::class.simpleName,
            ex.message,
        )

        val errorCode = ApiErrorCode.UNAUTHORIZED

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiError(code = errorCode.name, message = errorCode.toApiMessage))
    }

    @ExceptionHandler(Exception::class)
    fun handleAny(ex: Exception): ResponseEntity<ApiError> {
        val root = ex.cause ?: ex

        log.error(
            "Unhandled exception: {} - {}",
            root::class.simpleName,
            root.message,
        )

        val errorCode = ApiErrorCode.INTERNAL_ERROR

        return ResponseEntity
            .status(errorCode.status)
            .body(ApiError(code = errorCode.name, message = errorCode.toApiMessage))
    }
}
