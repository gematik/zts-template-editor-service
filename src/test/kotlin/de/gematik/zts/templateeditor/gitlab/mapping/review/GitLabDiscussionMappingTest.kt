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

package de.gematik.zts.templateeditor.gitlab.mapping.review

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime

class GitLabDiscussionMappingTest {

    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    @Test
    fun `deserializes discussion with note and nested position`() {
        val json = """
      {
        "id": "disc-123",
        "resolved": true,
        "notes": [
          {
            "id": 42,
            "body": "Please rename this variable.",
            "created_at": "2025-12-16T10:11:12+01:00",
            "resolved": false,
            "system": false,
            "author": {
              "id": 7,
              "name": "Alice",
              "username": "alice"
            },
            "position": {
              "position_type": "text",
              "base_sha": "base",
              "start_sha": "start",
              "head_sha": "head",
              "new_path": "src/Main.kt",
              "new_line": 10,
              "old_path": "src/Main.kt",
              "old_line": 9
            }
          }
        ]
      }
    """.trimIndent()

        val discussion: GitLabDiscussion = mapper.readValue(json)

        assertEquals("disc-123", discussion.id)
        assertEquals(true, discussion.resolved)
        assertEquals(1, discussion.notes.size)

        val note = discussion.notes.first()
        assertEquals(42L, note.id)
        assertEquals("Please rename this variable.", note.body)
        assertEquals(OffsetDateTime.parse("2025-12-16T09:11:12Z"), note.createdAt)
        assertEquals(false, note.resolved)
        assertEquals(false, note.system)

        assertNotNull(note.author)

        val pos = note.position
        assertNotNull(pos)
        assertEquals("text", pos!!.positionType)
        assertEquals("base", pos.baseSha)
        assertEquals("start", pos.startSha)
        assertEquals("head", pos.headSha)
        assertEquals("src/Main.kt", pos.newPath)
        assertEquals(10, pos.newLine)
        assertEquals("src/Main.kt", pos.oldPath)
        assertEquals(9, pos.oldLine)
    }

    @Test
    fun `deserializes note with optional fields missing`() {
        val json = """
      {
        "id": "disc-999",
        "notes": [
          {
            "id": 1,
            "body": "ok",
            "created_at": "2025-12-16T00:00:00Z"
          }
        ]
      }
    """.trimIndent()

        val discussion: GitLabDiscussion = mapper.readValue(json)

        assertEquals("disc-999", discussion.id)
        assertNull(discussion.resolved)
        assertEquals(1, discussion.notes.size)

        val note = discussion.notes.first()
        assertEquals(1L, note.id)
        assertEquals("ok", note.body)
        assertEquals(OffsetDateTime.parse("2025-12-16T00:00:00Z"), note.createdAt)

        assertNull(note.author)
        assertNull(note.resolved)
        assertNull(note.position)
        assertNull(note.system)
    }
}
