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

package de.gematik.zts.templateeditor.gitlab.exceptions.http.server

import de.gematik.zts.templateeditor.gitlab.constants.GitLabErrorCode
import de.gematik.zts.templateeditor.gitlab.exceptions.http.GitLabRuntimeException

/**
 * Exception for other 5xx server errors from GitLab API.
 * Thrown when an unexpected server error occurs.
 *
 * @param status HTTP status code of the server error (default: 500).
 * @param message Detailed error message.
 */
class GitLabUpstreamException(
    status: Int? = 502,
    message: String = "GitLab returned $status - upstream error.",
) : GitLabRuntimeException(
        GitLabErrorCode.BAD_GATEWAY,
        message,
    ) {
    /**
     * prints the German API response-message in a way that can be interpreted by the user in the front end.
     */
    override fun toApiMessage(): String =
        """
        Der GitLab-Dienst ist derzeit nicht erreichbar oder reagiert fehlerhaft.
        Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.
        """.trimIndent()
}
