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

package de.gematik.zts.templateeditor.gitlab.commits

import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange.Action.*
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange.Encoding.*
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange.Type.*
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import io.mockk.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

class GitLabActionBuilderTest {

    private lateinit var gl: GitLabClient
    private lateinit var builder: GitLabActionBuilder

    private val projectId = "123"
    private val branch = "dev"
    private val ver = "2025.0.0"
    private val indexPath = "$ver/metadata/website/resources-description/index.json"

    @BeforeEach
    fun setup() {
        gl = mockk()
        builder = GitLabActionBuilder(gl, ObjectMapper())
    }

    private fun mdPath(url: String, mdVersion: String) =
        "$ver/metadata/website/resources-description/" +
                url.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') +
                "-$ver-$mdVersion.md"

    private fun stubIndex(raw: String?) {
        every { gl.fetchFileTextInSingleLine(projectId, branch, indexPath) } answers {
            when (raw) {
                null -> Mono.empty()
                else -> Mono.just(raw)
            }
        }
    }

    private fun change(
        type: CommitChange.Type,
        action: CommitChange.Action,
        fileName: String,
        content: String? = null,
        encoding: CommitChange.Encoding = TEXT
    ) = CommitChange(action, type, fileName, content, encoding)

    @Test
    fun `no markdown - only other actions returned`() {
        stubIndex(null) // must not be hit

        val ch = change(METADATA, CREATE, "metadata.json", "{}")
        val actions = builder.buildGitLabActions(projectId, branch, ver, listOf(ch)).block()!!

        assertThat(actions).hasSize(1)
        assertThat(actions[0]).extracting("action", "filePath", "content", "encoding")
            .containsExactly("create", "$ver/metadata/metadata_package.json", "{}", "text")

        verify(exactly = 0) { gl.fetchFileTextInSingleLine(any(), any(), any()) }
    }

    @Test
    fun `create markdown - index exists`() {
        stubIndex("""{"resource-index":[]}""")
        val url = "1.0;https://x"
        val ch = change(TEMPLATE_MARKDOWN, CREATE, url, "# hi")

        val actions = builder.buildGitLabActions(projectId, branch,  ver, listOf(ch)).block()!!

        assertThat(actions).hasSize(2)

        val createMd = actions.find { it.action == "create" && it.filePath.endsWith(".md") }!!
        assertThat(createMd.filePath).isEqualTo(mdPath("https://x", "1.0"))
        assertThat(createMd.content).isEqualTo("# hi")

        val index = actions.find { it.filePath == indexPath }!!
        assertThat(index.action).isEqualTo("update")
        assertThat(index.content).contains("https://x")
    }

    @Test
    fun `create markdown - index missing (switchIfEmpty)`() {
        stubIndex(null)
        val url = "1.0;https://y"
        val ch = change(TEMPLATE_MARKDOWN, CREATE, url, "x")

        val actions = builder.buildGitLabActions(projectId, branch,  ver, listOf(ch)).block()!!

        assertThat(actions).hasSize(2)
        val index = actions.find { it.filePath == indexPath }!!
        assertThat(index.action).isEqualTo("create")
        assertThat(index.content).contains("https://y")
    }

    @Test
    fun `create markdown - index broken json (catch - existed=true)`() {
        stubIndex("{not json")
        val url = "1.0;http://broken"
        val ch = change(TEMPLATE_MARKDOWN, CREATE, url, "x")

        val actions = builder.buildGitLabActions(projectId, branch,  ver, listOf(ch)).block()!!

        val index = actions.find { it.filePath == indexPath }!!
        assertThat(index.action).isEqualTo("update")
        assertThat(index.content).contains("http://broken")
    }

    @Test
    fun `update markdown - upserts entry`() {
        stubIndex("""{"resource-index": []}""")
        val url = "1.0;http://u"
        val ch = change(TEMPLATE_MARKDOWN, UPDATE, url, "upd")

        val actions = builder.buildGitLabActions(projectId, branch,  ver, listOf(ch)).block()!!

        val updateMd = actions.find { it.action == "update" && it.filePath.endsWith(".md") }!!
        assertThat(updateMd.content).isEqualTo("upd")

        val index = actions.find { it.filePath == indexPath }!!
        assertThat(index.content).contains("http://u")
    }

    @Test
    fun `delete markdown - removes entry`() {
        stubIndex("""{"resource-index":[{"url":"http://del","version":"$ver","markdown-file":"x.md"}]}""")
        val url = "1.0;http://del"
        val ch = change(TEMPLATE_MARKDOWN, DELETE, url)

        val actions = builder.buildGitLabActions(projectId, branch, ver, listOf(ch)).block()!!

        val delete = actions.find { it.action == "delete" }!!
        assertThat(delete.filePath).isEqualTo(mdPath("http://del", "1.0"))

        val index = actions.find { it.filePath == indexPath }!!
        assertThat(index.content).doesNotContain(url)
    }

    @Test
    fun `index load returns non-404 GitLab error - rethrow`() {
        every { gl.fetchFileTextInSingleLine(projectId, branch, indexPath) } throws GitLabHttpException(500,"500")

        val url = "1.0;x"
        val ch = change(TEMPLATE_MARKDOWN, CREATE, url)

        assertThatThrownBy {
            builder.buildGitLabActions(projectId, branch, ver, listOf(ch)).block()
        }
            .isInstanceOf(GitLabHttpException::class.java)
    }
}
