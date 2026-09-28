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

class CommitChangeValidationTest {

    private fun cc(
        action: CommitChange.Action,
        content: String? = null,
        encoding: CommitChange.Encoding = CommitChange.Encoding.TEXT
    ) = CommitChange(
        action = action,
        type = CommitChange.Type.TEMPLATE,
        fileName = "input/templates/x.json",
        content = content,
        encoding = encoding
    )

    @Test
    fun `content valid - CREATE`() {
        assertTrue(cc(CommitChange.Action.CREATE, content = "x").isContentValidForAction)
        assertTrue(cc(CommitChange.Action.CREATE, content = null).isContentValidForAction)
        assertTrue(cc(CommitChange.Action.CREATE, content = "").isContentValidForAction)
        assertTrue(cc(CommitChange.Action.CREATE, content = "   ").isContentValidForAction)
    }

    @Test
    fun `content valid - UPDATE`() {
        assertTrue(cc(CommitChange.Action.UPDATE, content = "x").isContentValidForAction)
        assertTrue(cc(CommitChange.Action.UPDATE, content = null).isContentValidForAction)
        assertTrue(cc(CommitChange.Action.UPDATE, content = "").isContentValidForAction)
        assertTrue(cc(CommitChange.Action.UPDATE, content = " \n\t ").isContentValidForAction)
    }

    @Test
    fun `content valid - DELETE requires content omitted or blank`() {
        assertTrue(cc(CommitChange.Action.DELETE, content = null).isContentValidForAction)
        assertTrue(cc(CommitChange.Action.DELETE, content = "").isContentValidForAction)
        assertTrue(cc(CommitChange.Action.DELETE, content = "   ").isContentValidForAction)

        assertFalse(cc(CommitChange.Action.DELETE, content = "x").isContentValidForAction)
        assertFalse(cc(CommitChange.Action.DELETE, content = "  x  ").isContentValidForAction)
    }

    @Test
    fun `base64 valid - non-base64 encoding always true`() {
        assertTrue(cc(CommitChange.Action.UPDATE, content = null, encoding = CommitChange.Encoding.TEXT).isBase64ValidIfNeeded)
        assertTrue(cc(CommitChange.Action.UPDATE, content = "", encoding = CommitChange.Encoding.TEXT).isBase64ValidIfNeeded)
        assertTrue(cc(CommitChange.Action.UPDATE, content = "not base64", encoding = CommitChange.Encoding.TEXT).isBase64ValidIfNeeded)
    }

    @Test
    fun `base64 valid - BASE64 requires non-null content`() {
        assertFalse(cc(CommitChange.Action.UPDATE, content = null, encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)
    }

    @Test
    fun `base64 valid - BASE64 requires length divisible by 4`() {
        assertFalse(cc(CommitChange.Action.UPDATE, content = "A", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)       // 1
        assertFalse(cc(CommitChange.Action.UPDATE, content = "AA", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)     // 2
        assertFalse(cc(CommitChange.Action.UPDATE, content = "AAA", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)    // 3
        assertTrue(cc(CommitChange.Action.UPDATE, content = "AAAA", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)    // 4
    }

    @Test
    fun `base64 valid - BASE64 allows standard alphabet plus padding`() {
        // "Zg==" == "f"
        assertTrue(cc(CommitChange.Action.UPDATE, content = "Zg==", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)

        // plus and slash are allowed by regex
        assertTrue(cc(CommitChange.Action.UPDATE, content = "AA+/AA==", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)
    }

    @Test
    fun `base64 valid - BASE64 rejects illegal characters`() {
        // length % 4 == 0 but contains illegal char '#'
        assertFalse(cc(CommitChange.Action.UPDATE, content = "AA#A", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)

        // whitespace (space/tab) not allowed; CR/LF are allowed explicitly
        assertFalse(cc(CommitChange.Action.UPDATE, content = "AAAA AAAA", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)
        assertFalse(cc(CommitChange.Action.UPDATE, content = "AAAA\tAAAA", encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)
    }

    @Test
    fun `base64 valid - BASE64 allows CRLF newlines`() {
        // 4 chars including CRLF -> length 4? nope. So we build a valid length:
        // "QUJD" == "ABC", add CRLF then "REVG" == "DEF" => total length 4+2+4 = 10 not divisible by 4 -> must fail
        // => we need length divisible by 4 even with CRLF included.
        // Example: insert CRLF but keep total length multiple of 4: 4 + 2 + 6 = 12 works, but 6 must be valid base64 chars.
        val content = "QUJD\r\nREVGQQ" // 4 + 2 + 6 = 12, all allowed chars
        assertTrue(cc(CommitChange.Action.UPDATE, content = content, encoding = CommitChange.Encoding.BASE64).isBase64ValidIfNeeded)
    }
}
