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

package de.gematik.zts.templateeditor.domain.workspaces

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommitChangeInputFileValidationTest {

    private fun ccInputFile(
        action: CommitChange.Action,
        fileName: String,
        encoding: CommitChange.Encoding = CommitChange.Encoding.TEXT,
        content: String? = "x"
    ) = CommitChange(
        action = action,
        type = CommitChange.Type.INPUT_FILE,
        fileName = fileName,
        content = content,
        encoding = encoding
    )

    private fun ccNonInputFileType(
        action: CommitChange.Action = CommitChange.Action.UPDATE,
        fileName: String = "whatever",
        encoding: CommitChange.Encoding = CommitChange.Encoding.TEXT,
        content: String? = "x"
    ) = CommitChange(
        action = action,
        type = CommitChange.Type.TEMPLATE, // != INPUT_FILE
        fileName = fileName,
        content = content,
        encoding = encoding
    )

    @Test
    fun `isFileValid - non INPUT_FILE type always true`() {
        assertTrue(ccNonInputFileType(fileName = "foo.zip", encoding = CommitChange.Encoding.TEXT).isFileValid)
        assertTrue(ccNonInputFileType(fileName = "input/foo.zip", encoding = CommitChange.Encoding.TEXT).isFileValid)
        assertTrue(ccNonInputFileType(fileName = "not-a-zip.txt", encoding = CommitChange.Encoding.TEXT).isFileValid)
        assertTrue(ccNonInputFileType(fileName = "a/b/c", encoding = CommitChange.Encoding.TEXT).isFileValid)
    }

    @Test
    fun `isFileValid - create or update requires BASE64 encoding`() {
        assertFalse(ccInputFile(action = CommitChange.Action.CREATE, fileName = "a.zip", encoding = CommitChange.Encoding.TEXT).isFileValid)
        assertFalse(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a.zip", encoding = CommitChange.Encoding.TEXT).isFileValid)

        assertTrue(ccInputFile(action = CommitChange.Action.CREATE, fileName = "a.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
        assertTrue(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
    }

    @Test
    fun `isFileValid - delete does not require BASE64 encoding`() {
        assertTrue(ccInputFile(action = CommitChange.Action.DELETE, fileName = "a.zip", encoding = CommitChange.Encoding.TEXT, content = null).isFileValid)
        assertTrue(ccInputFile(action = CommitChange.Action.DELETE, fileName = "a.zip", encoding = CommitChange.Encoding.BASE64, content = null).isFileValid)
    }

    @Test
    fun `isFileValid - filename must not contain slashes`() {
        assertTrue(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
        assertTrue(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a", encoding = CommitChange.Encoding.BASE64).isFileValid)

        assertFalse(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "input/a.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
        assertFalse(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a/b.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
        assertFalse(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "/a.zip", encoding = CommitChange.Encoding.BASE64).isFileValid)
    }

    @Test
    fun `isFileValid - encoding rule is enforced before filename rule`() {
        assertFalse(ccInputFile(action = CommitChange.Action.UPDATE, fileName = "a.zip", encoding = CommitChange.Encoding.TEXT).isFileValid)

        assertTrue(ccInputFile(action = CommitChange.Action.DELETE, fileName = "a.zip", encoding = CommitChange.Encoding.TEXT, content = null).isFileValid)
        assertFalse(ccInputFile(action = CommitChange.Action.DELETE, fileName = "input/a.zip", encoding = CommitChange.Encoding.TEXT, content = null).isFileValid)
    }
}
