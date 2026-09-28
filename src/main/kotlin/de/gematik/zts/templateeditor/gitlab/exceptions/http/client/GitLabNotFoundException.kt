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

package de.gematik.zts.templateeditor.gitlab.exceptions.http.client

import de.gematik.zts.templateeditor.gitlab.constants.GitLabErrorCode
import de.gematik.zts.templateeditor.gitlab.exceptions.http.GitLabRuntimeException

/**
 * Exception for HTTP 404 Not Found from GitLab API.
 * Thrown when the requested resource does not exist.
 *
 * MessaThe requested item could not be found.
 */
class GitLabNotFoundException(
    message: String = "GitLab API returned HTTP 404 - Resource not found.",
) : GitLabRuntimeException(
        GitLabErrorCode.NOT_FOUND,
        message,
    ) {
    /**
     * prints the German API response-message in a way that can be interpreted by the user in the front end.
     */
    override fun toApiMessage(): String =
        """
        Die angeforderte GitLab-Ressource wurde nicht gefunden.
        Versuchen Sie es später erneut oder wenden sie sich an den Servicebetreiber.
        """.trimIndent()
}
