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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileTypeValidatorTest {
    private var fileTypeValidator = FileTypeValidator()

    private fun xmlContent(resourceType: String) =
        """  
        <$resourceType xmlns="http://hl7.org/fhir">               
            <id value="some-terminology-2026"/>            
        </$resourceType>    
        """.trimIndent().toByteArray()

    private fun jsonContent(resourceType: String) =
        """
        {"resourceType":"$resourceType", "id_value": 2}
        """.trimIndent().toByteArray()

    private fun getTestZipStream(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("test.txt"))
            zos.write("content".toByteArray())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private val validResourceType = listOf<String>("ValueSet", "ConceptMap", "CodeSystem")

    @Test
    fun `isFileTypeZip returns true if file is zip`() {
        assertTrue(fileTypeValidator.isFileTypeZip(getTestZipStream()))
    }

    @Test
    fun `isFileTypeZip returns false if file is not zip`() {
        assertFalse(fileTypeValidator.isFileTypeZip(jsonContent("someResource")))
    }

    @Test
    fun `isFileTypeJson returns true if file is json`() {
        assertTrue(fileTypeValidator.isFileTypeJson(jsonContent("someResource")))
    }

    @Test
    fun `ifFileTypeJson returns false if file not json`() {
        assertFalse(fileTypeValidator.isFileTypeJson(xmlContent("someResource")))
    }

    @Test
    fun `ifValidFhirJson returns true if file containing a valid fhir json with valid rt`() {
        validResourceType.forEach { rt ->
            assertTrue(fileTypeValidator.isValidFhirJson(jsonContent(rt)))
        }
    }

    @Test
    fun `ifValidFhirJson returns false if file containing a fhir json with invalid rt`() {
        assertFalse(fileTypeValidator.isValidFhirJson(jsonContent("someResource")))
    }

    @Test
    fun `isFileTypeJson returns true if file is xml`() {
        assertTrue(fileTypeValidator.isFileTypeXml(xmlContent("someResource")))
    }

    @Test
    fun `ifFileTypeXml returns false if file not xml`() {
        assertFalse(fileTypeValidator.isFileTypeXml(jsonContent("someResource")))
    }

    @Test
    fun `ifValidFhirXml returns true if file containing a valid fhir xml with valid rt`() {
        validResourceType.forEach { rt ->
            assertTrue(fileTypeValidator.isValidFhirXml(xmlContent(rt)))
        }
    }

    @Test
    fun `ifValidFhirXml returns false if file containing a fhir xml with invalid rt`() {
        assertFalse(fileTypeValidator.isValidFhirXml(xmlContent("someResource")))
    }
}
