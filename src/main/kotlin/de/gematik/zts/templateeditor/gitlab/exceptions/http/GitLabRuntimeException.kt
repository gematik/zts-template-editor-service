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

package de.gematik.zts.templateeditor.gitlab.exceptions.http
import de.gematik.zts.templateeditor.gitlab.constants.GitLabErrorCode

/**
 * Base exception for all GitLab-related runtime exceptions.
 *
 * This class intentionally extends [RuntimeException] instead of a checked
 * exception because Spring WebFlux and Project Reactor are designed to work
 * primarily with unchecked exceptions.
 *
 * Reasons:
 * - Reactive pipelines (Mono/Flux) propagate errors as Throwable signals.
 * - Checked exceptions do not integrate well with functional/reactive chains.
 * - RuntimeExceptions do not require explicit "throws" declarations.
 * - Spring’s global error handling automatically handles RuntimeExceptions.
 *
 * Using RuntimeException keeps the API clean, avoids boilerplate, and follows
 * Spring WebFlux best practices.
 * @property gitLabErrorCode Enum-Class with values mapped to HttpStatusCodes.
 * @param message Optional: detailed error message.
 */
open class GitLabRuntimeException(
    val gitLabErrorCode: GitLabErrorCode,
    message: String,
) : RuntimeException(message) {
    /**
     * prints the German API response-message in a way that can be interpreted by the user in the front end.
     */
    open fun toApiMessage(): String =
        """
        Bei der Verarbeitung Ihrer Anfrage ist ein Fehler aufgetreten.
        Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.
        """.trimIndent()
}
