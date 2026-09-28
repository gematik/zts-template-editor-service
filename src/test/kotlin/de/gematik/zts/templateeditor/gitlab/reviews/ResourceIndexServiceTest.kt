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

package de.gematik.zts.templateeditor.gitlab.reviews

import de.gematik.zts.templateeditor.domain.workspaces.ResourceIndex
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class ResourceIndexServiceTest {
    private val gl = mockk<GitLabClient>()
    private val mapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()
    private val service = ResourceIndexService(gl, mapper)

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `load parses json into ResourceIndex`() {
        val json =
            """
            {
              "resource-index": [
                { "url": "u1", "version": "1.0", "markdown-file": "m1.md" },
                { "url": "u2", "version": "1.0", "markdown-file": "m2.md" }
              ]
            }
            """.trimIndent()

        every {
            gl.fetchFileTextInSingleLine(
                "p1",
                "b1",
                "1.0.0/metadata/website/resources-description/index.json",
            )
        } returns Mono.just(json)

        val result = service.load("p1", "b1", "1.0.0").block()

        assertThat(result).isNotNull
        assertThat(result!!.items).hasSize(2)
        assertThat(result.items[0].url).isEqualTo("u1")
        assertThat(result.items[0].markdownFile).isEqualTo("m1.md")
    }

    @Test
    fun `load returns empty index when json cannot be parsed`() {
        every {
            gl.fetchFileTextInSingleLine(
                any(),
                any(),
                any(),
            )
        } returns Mono.just("{not valid json")

        val result = service.load("p1", "b1", "1.0.0").block()

        assertThat(result).isEqualTo(ResourceIndex(mutableListOf()))
    }

    @Test
    fun `load returns empty index when fetch is empty`() {
        every {
            gl.fetchFileTextInSingleLine(
                any(),
                any(),
                any(),
            )
        } returns Mono.empty()

        val result = service.load("p1", "b1", "1.0.0").block()

        assertThat(result).isEqualTo(ResourceIndex(mutableListOf()))
    }

    @Test
    fun `load returns empty index on 404 GitLabHttpException`() {
        every {
            gl.fetchFileTextInSingleLine(
                any(),
                any(),
                any(),
            )
        } returns Mono.error(GitLabHttpException(404, "Not Found"))

        val result = service.load("p1", "b1", "1.0.0").block()

        assertThat(result).isEqualTo(ResourceIndex(mutableListOf()))
    }

    @Test
    fun `load rethrows non-404 GitLabHttpException`() {
        every {
            gl.fetchFileTextInSingleLine(
                any(),
                any(),
                any(),
            )
        } returns Mono.error(GitLabHttpException(500, "Internal Server Error"))

        val ex =
            org.junit.jupiter.api.assertThrows<GitLabHttpException> {
                service.load("p1", "b1", "1.0.0").block()
            }

        assertThat(ex.message).contains("500")
    }

    @Test
    fun `load caches result - fetch called only once for multiple subscribers`() {
        val json = """{ "items": [ { "url": "u1", "markdownFile": "m1.md" } ] }"""

        every {
            gl.fetchFileTextInSingleLine(
                "p1",
                "b1",
                "1.0.0/metadata/website/resources-description/index.json",
            )
        } returns Mono.just(json)

        val mono = service.load("p1", "b1", "1.0.0")

        val r1 = mono.block()
        val r2 = mono.block()

        assertThat(r1).isEqualTo(r2)
        verify(exactly = 1) {
            gl.fetchFileTextInSingleLine(
                "p1",
                "b1",
                "1.0.0/metadata/website/resources-description/index.json",
            )
        }
    }
}
