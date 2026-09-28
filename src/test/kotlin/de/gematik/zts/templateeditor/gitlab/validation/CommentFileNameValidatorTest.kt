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

import de.gematik.zts.templateeditor.domain.reviews.CommentCreateRequest
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommentFileNameValidatorTest {
    private val validator = CommentFileNameValidator()

    private fun request(
        type: CommitChange.Type,
        fileName: String,
    ) = CommentCreateRequest(
        repositoryId = "123",
        branch = "feature/test",
        mrId = "17",
        version = "1.2.3",
        type = type,
        fileName = fileName,
        line = 42,
        body = "Some valid comment",
    )

    @Test
    fun `isValid returns true for null value`() {
        val context = mockk<ConstraintValidatorContext>(relaxed = true)

        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `isValid returns true for regular type with valid default fileName`() {
        val context = mockk<ConstraintValidatorContext>(relaxed = true)

        assertTrue(
            validator.isValid(
                request(
                    type = CommitChange.Type.TEMPLATE,
                    fileName = "folder/my.template.json",
                ),
                context,
            ),
        )
    }

    @Test
    fun `isValid returns false for regular type with semicolon in fileName`() {
        val context = mockk<ConstraintValidatorContext>()
        val violationBuilder = mockk<ConstraintValidatorContext.ConstraintViolationBuilder>(relaxed = true)
        val nodeBuilder =
            mockk<ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext>(relaxed = true)

        every { context.disableDefaultConstraintViolation() } returns Unit
        every { context.buildConstraintViolationWithTemplate(any()) } returns violationBuilder
        every { violationBuilder.addPropertyNode("fileName") } returns nodeBuilder
        every { nodeBuilder.addConstraintViolation() } returns context

        assertFalse(
            validator.isValid(
                request(
                    type = CommitChange.Type.TEMPLATE,
                    fileName = "2.82.0;https://terminologien.bfarm.de/fhir/CodeSystem/test",
                ),
                context,
            ),
        )

        verify { context.disableDefaultConstraintViolation() }
        verify {
            context.buildConstraintViolationWithTemplate(
                "fileName '2.82.0;https://terminologien.bfarm.de/fhir/CodeSystem/test' is invalid for type 'template'",
            )
        }
        verify { violationBuilder.addPropertyNode("fileName") }
        verify { nodeBuilder.addConstraintViolation() }
    }

    @Test
    fun `isValid returns true for TEMPLATE_MARKDOWN with semicolon and colon`() {
        val context = mockk<ConstraintValidatorContext>(relaxed = true)

        assertTrue(
            validator.isValid(
                request(
                    type = CommitChange.Type.TEMPLATE_MARKDOWN,
                    fileName = "2.82.0;https://terminologien.bfarm.de/fhir/CodeSystem/loinc-linguistic-variant-de-de",
                ),
                context,
            ),
        )
    }

    @Test
    fun `isValid returns false for TEMPLATE_MARKDOWN with backslash`() {
        val context = mockk<ConstraintValidatorContext>()
        val violationBuilder = mockk<ConstraintValidatorContext.ConstraintViolationBuilder>(relaxed = true)
        val nodeBuilder =
            mockk<ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext>(relaxed = true)

        every { context.disableDefaultConstraintViolation() } returns Unit
        every { context.buildConstraintViolationWithTemplate(any()) } returns violationBuilder
        every { violationBuilder.addPropertyNode("fileName") } returns nodeBuilder
        every { nodeBuilder.addConstraintViolation() } returns context

        assertFalse(
            validator.isValid(
                request(
                    type = CommitChange.Type.TEMPLATE_MARKDOWN,
                    fileName = """2.82.0;https:\bad\path""",
                ),
                context,
            ),
        )

        verify { context.disableDefaultConstraintViolation() }
        verify {
            context.buildConstraintViolationWithTemplate(
                """fileName '2.82.0;https:\bad\path' is invalid for type 'template_markdown'""",
            )
        }
        verify { violationBuilder.addPropertyNode("fileName") }
        verify { nodeBuilder.addConstraintViolation() }
    }

    @Test
    fun `isValid returns false for TEMPLATE_MARKDOWN with parent traversal`() {
        val context = mockk<ConstraintValidatorContext>()
        val violationBuilder = mockk<ConstraintValidatorContext.ConstraintViolationBuilder>(relaxed = true)
        val nodeBuilder =
            mockk<ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext>(relaxed = true)

        every { context.disableDefaultConstraintViolation() } returns Unit
        every { context.buildConstraintViolationWithTemplate(any()) } returns violationBuilder
        every { violationBuilder.addPropertyNode("fileName") } returns nodeBuilder
        every { nodeBuilder.addConstraintViolation() } returns context

        assertFalse(
            validator.isValid(
                request(
                    type = CommitChange.Type.TEMPLATE_MARKDOWN,
                    fileName = "../evil",
                ),
                context,
            ),
        )

        verify { context.disableDefaultConstraintViolation() }
        verify {
            context.buildConstraintViolationWithTemplate(
                "fileName '../evil' is invalid for type 'template_markdown'",
            )
        }
        verify { violationBuilder.addPropertyNode("fileName") }
        verify { nodeBuilder.addConstraintViolation() }
    }
}
