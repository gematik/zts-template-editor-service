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

package de.gematik.zts.templateeditor.web.constants

import org.springframework.http.HttpStatus

/**
 * Represents standardized error codes for API responses.
 *
 * Each entry defines:
 * - the corresponding HTTP status code (`status`)
 * - a user-friendly error message (`toApiMessage`)
 *   that can be returned in the API response.
 *
 * The included messages are intentionally generic
 * to avoid exposing sensitive implementation details.
 */
enum class ApiErrorCode(
    val status: HttpStatus, val toApiMessage: String
) {

    // 400
    BAD_REQUEST(
        HttpStatus.BAD_REQUEST,
        "Die Anfrage ist ungültig. Bitte überprüfen Sie Ihre Eingaben und versuchen Sie es erneut."
    ),

    VALIDATION_FAILED(
        HttpStatus.BAD_REQUEST,
        "Ein oder mehrere Eingabewerte sind ungültig. Bitte prüfen Sie Ihre Angaben und korrigieren Sie die markierten Felder."
    ),

    //401
    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "Sie sind nicht autorisiert, diese Ressource zuzugreifen. Bitte melden Sie sich an oder überprüfen Sie Ihre Berechtigungen."
    ),

    // 500
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Beim Verarbeiten der Anfrage ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es später erneut oder kontaktieren Sie den Servicebetreiber."
    )
}
