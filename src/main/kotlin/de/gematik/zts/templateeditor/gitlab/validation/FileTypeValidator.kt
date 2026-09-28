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

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

@Component
class FileTypeValidator {
    private val mapper = ObjectMapper()

    /** Thread-safe factory — new DocumentBuilder created per call to avoid shared mutable state. */
    private val dbf =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true // for FHIR XML namespaces
            }

    // InputStream-based methods (preferred — avoids loading full byte[] into heap)

    /**
     * Tests if the stream starts with the ZIP magic number (PK\x03\x04).
     * Only reads the first 4 bytes.
     */
    fun isFileTypeZip(input: InputStream): Boolean {
        val header = ByteArray(4)
        val read = input.readNBytes(header, 0, 4)
        return read == 4 && header.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }

    /**
     * Tests if the stream contains valid XML by parsing with a fresh DocumentBuilder.
     */
    fun isFileTypeXml(input: InputStream): Boolean =
        try {
            dbf.newDocumentBuilder().parse(input)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Tests if the stream contains valid JSON by parsing with ObjectMapper.
     */
    fun isFileTypeJson(input: InputStream): Boolean =
        try {
            mapper.readTree(input)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * Tests if the stream contains valid FHIR JSON (resourceType must be ValueSet, ConceptMap, or CodeSystem).
     */
    fun isValidFhirJson(input: InputStream): Boolean =
        try {
            val json = mapper.readTree(input)
            ValidResourceType.isValid(json["resourceType"].asString())
        } catch (_: Exception) {
            false
        }

    /**
     * Tests if the stream contains valid FHIR XML (root tag must be ValueSet, ConceptMap, or CodeSystem).
     */
    fun isValidFhirXml(input: InputStream): Boolean =
        try {
            val doc = dbf.newDocumentBuilder().parse(input)
            ValidResourceType.isValid(doc.documentElement.localName)
        } catch (_: Exception) {
            false
        }

    // ByteArray-based methods (delegate to InputStream versions)

    fun isFileTypeZip(file: ByteArray): Boolean = isFileTypeZip(ByteArrayInputStream(file))

    fun isFileTypeXml(file: ByteArray): Boolean = isFileTypeXml(ByteArrayInputStream(file))

    fun isFileTypeJson(file: ByteArray): Boolean = isFileTypeJson(ByteArrayInputStream(file))

    fun isValidFhirJson(file: ByteArray): Boolean = isValidFhirJson(ByteArrayInputStream(file))

    fun isValidFhirXml(file: ByteArray): Boolean = isValidFhirXml(ByteArrayInputStream(file))

    /**
     * enum with accepted Resource types
     */
    private enum class ValidResourceType(
        val value: String,
    ) {
        VALUE_SET("ValueSet"),
        CONCEPT_MAP("ConceptMap"),
        CODE_SYSTEM("CodeSystem"),
        ;

        companion object {
            fun isValid(value: String?): Boolean {
                if (value == null) return false
                return entries.any { it.value == value }
            }
        }
    }
}
