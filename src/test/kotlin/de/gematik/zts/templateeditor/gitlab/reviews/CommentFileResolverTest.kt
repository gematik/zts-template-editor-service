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

import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.resolvePath
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommentFileResolverTest {
    private val resolver = CommentFileResolver()

    @Test
    fun `template path maps to TEMPLATE and relative filename`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/input/templates/foo/bar.template.json",
                version = "1.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.TEMPLATE)
        assertThat(file).isEqualTo("foo/bar.template.json")
        verify(exactly = 0) { index.urlForMarkdown(any()) }
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
    }

    @Test
    fun `package template maps to PACKAGE_TEMPLATE`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "2.0.0/input/templates/package/package.template.json",
                version = "2.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.PACKAGE_TEMPLATE)
        assertThat(file).isEqualTo("package.template.json")
    }

    @Test
    fun `metadata file maps to METADATA`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "3.1.0/metadata/metadata_package.json",
                version = "3.1.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.METADATA)
        assertThat(file).isEqualTo("metadata_package.json")
    }

    @Test
    fun `changelog file maps to CHANGELOGS`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "3.1.0/metadata/changelog.json",
                version = "3.1.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.CHANGELOGS)
        assertThat(file).isEqualTo("changelog.json")
    }

    @Test
    fun `package markdown maps to PACKAGE_MARKDOWN and strips prefix`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.2.3/metadata/website/package/readme.md",
                version = "1.2.3",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.PACKAGE_MARKDOWN)
        assertThat(file).isEqualTo("readme.md")
    }

    @Test
    fun `template markdown uses index mapping from markdown to url`() {
        val index = mockk<ResourceIndexLookup>()
        every { index.urlForMarkdown("foo.md") } returns "https://example.test/foo"

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "9.9.9/metadata/website/resources-description/foo.md",
                version = "9.9.9",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.TEMPLATE_MARKDOWN)
        assertThat(file).isEqualTo("https://example.test/foo")

        verify(exactly = 1) { index.urlForMarkdown("foo.md") }
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
    }

    @Test
    fun `template markdown falls back to markdown name when index has no mapping`() {
        val index = mockk<ResourceIndexLookup>()
        every { index.urlForMarkdown("foo.md") } returns null

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "9.9.9/metadata/website/resources-description/foo.md",
                version = "9.9.9",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.TEMPLATE_MARKDOWN)
        assertThat(file).isEqualTo("foo.md")

        verify(exactly = 1) { index.urlForMarkdown("foo.md") }
    }

    @Test
    fun `index json maps to UPDATE_INDEX_URL_VERSION`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/metadata/website/resources-description/index.json",
                version = "1.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.UPDATE_INDEX_URL_VERSION)
        assertThat(file).isEqualTo("index.json")
    }

    @Test
    fun `unknown path falls back to TEMPLATE and uses last segment`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/some/unknown/path/whatever.txt",
                version = "1.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.TEMPLATE)
        assertThat(file).isEqualTo("whatever.txt")
    }

    @Test
    fun `resolveGitLabPath for TEMPLATE_MARKDOWN uses index mapping url to markdown`() {
        val index = mockk<ResourceIndexLookup>()
        every { index.markdownForUrl("https://example.test/foo", "1.0") } returns "foo.md"

        val path =
            resolver.resolveGitLabPath(
                fileName = "1.0;https://example.test/foo",
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                version = "1.0.0",
                index = index,
            )

        assertThat(path).isEqualTo("1.0.0/metadata/website/resources-description/foo.md")
        verify(exactly = 1) { index.markdownForUrl("https://example.test/foo", "1.0") }
        verify(exactly = 0) { index.urlForMarkdown(any()) }
    }

    @Test
    fun `resolveGitLabPath for TEMPLATE_MARKDOWN falls back to fileName if index has no mapping`() {
        val index = mockk<ResourceIndexLookup>()
        every { index.markdownForUrl("foo.md", "1.0") } returns null

        val path =
            resolver.resolveGitLabPath(
                fileName = "1.0;foo.md",
                type = CommitChange.Type.TEMPLATE_MARKDOWN,
                version = "1.0.0",
                index = index,
            )

        assertThat(path).isEqualTo("1.0.0/metadata/website/resources-description/1.0;foo.md")
        verify(exactly = 1) { index.markdownForUrl("foo.md", "1.0") }
    }

    @Test
    fun `resolveGitLabPath for non markdown delegates to resolvePath`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val path =
            resolver.resolveGitLabPath(
                fileName = "main.json",
                type = CommitChange.Type.TEMPLATE,
                version = "1.0.0",
                index = index,
            )

        assertThat(path).isEqualTo(CommitChange.Type.TEMPLATE.resolvePath("main.json", "1.0.0"))
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
        verify(exactly = 0) { index.urlForMarkdown(any()) }
    }

    @Test
    fun `download conditions file maps to DOWNLOAD_CONDITIONS`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "3.1.0/metadata/download-conditions.xml",
                version = "3.1.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.DOWNLOAD_CONDITIONS)
        assertThat(file).isEqualTo("download-conditions.xml")
        verify(exactly = 0) { index.urlForMarkdown(any()) }
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
    }

    @Test
    fun `input top-level file maps to INPUT_FILE and strips input prefix`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/input/foo.json",
                version = "1.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.INPUT_FILE)
        assertThat(file).isEqualTo("foo.json")
        verify(exactly = 0) { index.urlForMarkdown(any()) }
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
    }

    @Test
    fun `input nested file does not map to INPUT_FILE when deeper than one slash`() {
        val index = mockk<ResourceIndexLookup>(relaxed = true)

        val (type, file) =
            resolver.resolveFromGitLabPath(
                fullPath = "1.0.0/input/foo/bar.json",
                version = "1.0.0",
                index = index,
            )

        assertThat(type).isEqualTo(CommitChange.Type.TEMPLATE)
        assertThat(file).isEqualTo("bar.json")
        verify(exactly = 0) { index.urlForMarkdown(any()) }
        verify(exactly = 0) { index.markdownForUrl(any(), any()) }
    }
}
