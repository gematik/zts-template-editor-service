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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileNameValidationTest {
    @Test
    fun `returns true for regular type with valid relative path`() {
        assertTrue(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE,
                fileName = "input/templates/medication.template.json",
            ),
        )
    }

    @Test
    fun `returns false for regular type with semicolon separated value`() {
        assertFalse(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE,
                fileName = "2026;https://dvmd.de/fhir/CodeSystem/kdl",
            ),
        )
    }

    @Test
    fun `returns true for TEMPLATE_MARKDOWN with semicolon separated url`() {
        assertTrue(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                fileName = "2026;https://dvmd.de/fhir/CodeSystem/kdl",
            ),
        )
    }

    @Test
    fun `returns false for TEMPLATE_MARKDOWN with leading slash`() {
        assertFalse(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                fileName = "/2026;https://dvmd.de/fhir/CodeSystem/kdl",
            ),
        )
    }

    @Test
    fun `returns false for TEMPLATE_MARKDOWN with backslash`() {
        assertFalse(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                fileName = """2026;https:\dvmd.de\fhir\CodeSystem\kdl""",
            ),
        )
    }

    @Test
    fun `returns false for TEMPLATE_MARKDOWN with parent traversal at start`() {
        assertFalse(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                fileName = "../evil",
            ),
        )
    }

    @Test
    fun `returns false for TEMPLATE_MARKDOWN with nested parent traversal`() {
        assertFalse(
            FileNameValidation.isValid(
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                fileName = "foo/../bar",
            ),
        )
    }
}
