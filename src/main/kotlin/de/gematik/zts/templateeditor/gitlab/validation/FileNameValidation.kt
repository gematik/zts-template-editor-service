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

package de.gematik.zts.templateeditor.gitlab.validation

import de.gematik.zts.templateeditor.domain.workspaces.CommitChange

object FileNameValidation {
    /**
     * Default validation for regular file names and relative paths.
     *
     * Rules:
     * - must not start with '/'
     * - must not contain backslashes
     * - must not contain '..' as a path segment
     * - allows letters, digits, '.', '_', '-', '/'
     */
    private val defaultFileNamePattern =
        Regex("""^(?!/)(?!.*\\)(?!.*(^|/)\.\.(/|$))[A-Za-z0-9._\-/]+$""")

    /**
     * Special validation for TEMPLATE_MARKDOWN references.
     *
     * This type may contain a semicolon-separated value such as:
     * 2026;https://dvmd.de/fhir/CodeSystem/kdl
     *
     * Compared to the default pattern, this variant additionally allows ';' and ':'.
     */
    private val templateMarkdownFileNamePattern =
        Regex("""^(?!/)(?!.*\\)(?!.*(^|/)\.\.(/|$))[A-Za-z0-9._\-/;:]+$""")

    fun isValid(
        type: CommitChange.Type,
        fileName: String,
    ): Boolean {
        val pattern =
            if (type == CommitChange.Type.TEMPLATE_MARKDOWN) {
                templateMarkdownFileNamePattern
            } else {
                defaultFileNamePattern
            }

        return pattern.matches(fileName)
    }

}