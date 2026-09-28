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

package de.gematik.zts.templateeditor.gitlab.fetcher

import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.domain.workspaces.TemplateMdItem
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import de.gematik.zts.templateeditor.gitlab.mapping.GitLabTreeItemDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.util.Optional

class DetailFetcherTest {
    private lateinit var gl: GitLabClient
    private lateinit var fetcher: DetailFetcher

    private val projectId = "123"
    private val branch = "main"
    private val version = "2025.0.0"

    @BeforeEach
    fun setup() {
        gl = mockk()
        fetcher = DetailFetcher(gl, ObjectMapper())
    }

    private fun mockFile(
        path: String,
        content: String,
    ) = every { gl.fetchFileTextInSingleLine(projectId, branch, path) } returns Mono.just(content)

    private fun mockFileEmpty(path: String) = every { gl.fetchFileTextInSingleLine(projectId, branch, path) } returns Mono.empty()

    private fun mockFileLines(
        path: String,
        content: String,
    ) = every { gl.fetchFileTextLines(projectId, branch, path) } returns Mono.just(content)

    private fun mockTree(vararg items: GitLabTreeItemDto) =
        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } answers {
            Flux.fromIterable(items.toList())
        }

    @Test
    fun `details - simplest successful case`() {
        mockFileEmpty("$version/metadata/website/resources-description/index.json")

        mockTree(
            GitLabTreeItemDto(
                name = "t1.template.json",
                type = "blob",
                path = "$version/input/templates/t1.template.json",
            ),
        )

        mockFile(
            "$version/input/templates/t1.template.json",
            """{"url":"http://x"}""",
        )

        listOf(
            "$version/input/templates/package/package.template.json",
            "$version/metadata/metadata_package.json",
            "$version/metadata/changelog.json",
        ).forEach { mockFileEmpty(it) }

        every { gl.fetchFileTextLines(projectId, branch, any()) } returns Mono.empty()

        val mrClazz =
            DetailFetcher::class.java.declaredClasses.first { it.simpleName == "GitLabMergeRequest" }
        @Suppress("UNCHECKED_CAST")
        every { gl.getFlux(clazz = mrClazz as Class<Any>, query = any()) } returns Flux.empty()
        val commitClazz =
            DetailFetcher::class.java.declaredClasses.first { it.simpleName == "GitLabCommitListItemDto" }

        val commitCtor = commitClazz.declaredConstructors.single()
        commitCtor.isAccessible = true
        val commitObj = commitCtor.newInstance("2026-02-20T08:00:00+01:00") // committed_date

        @Suppress("UNCHECKED_CAST")
        every { gl.getFlux(clazz = commitClazz as Class<Any>, query = any()) } returns Flux.just(commitObj)

        val result = fetcher.details(projectId, branch, version).block()!!

        assertThat(result.templateJsons).hasSize(1)
        assertThat(result.templateJsons[0].name).isEqualTo("t1.template.json")
        assertThat(result.mergeRequestRef).isNull()
        assertThat(result.templateMds).isEmpty()
    }

    @Test
    fun `loadMarkdowns - valid index with markdown`() {
        val indexPath = "$version/metadata/website/resources-description/index.json"

        mockFile(
            indexPath,
            """{"resource-index":[{"url":"http://a","version":"1.0","markdown-file":"a.md"}]}""",
        )
        mockFileLines(
            "$version/metadata/website/resources-description/a.md",
            "hello",
        )

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "loadMarkdowns",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val list =
            (method.invoke(fetcher, projectId, branch, version) as Flux<TemplateMdItem>)
                .collectList()
                .block()!!

        assertThat(list).hasSize(1)
        assertThat(list[0]).isEqualTo(
            TemplateMdItem(
                url = "http://a",
                markdown = "hello",
                version = "1.0",
            ),
        )
    }

    @Test
    fun `loadMarkdowns - missing index returns empty list`() {
        val indexPath = "$version/metadata/website/resources-description/index.json"
        mockFileEmpty(indexPath)

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "loadMarkdowns",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val list =
            (method.invoke(fetcher, projectId, branch, version) as Flux<TemplateMdItem>)
                .collectList()
                .block()!!

        assertThat(list).isEmpty()
    }

    @Test
    fun `loadMarkdowns - broken index json results in empty list`() {
        val indexPath = "$version/metadata/website/resources-description/index.json"
        mockFile(indexPath, "{broken")

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "loadMarkdowns",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val list =
            (method.invoke(fetcher, projectId, branch, version) as Flux<TemplateMdItem>)
                .collectList()
                .block()!!

        assertThat(list).isEmpty()
    }

    @Test
    fun `listTemplateJsons - 404 in tree - empty list`() {
        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } returns
            Flux.error(GitLabHttpException(404, "404"))

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "listTemplateJsons",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val list =
            (method.invoke(fetcher, projectId, branch, version) as Mono<List<*>>)
                .block()!!

        assertThat(list).isEmpty()
    }

    @Test
    fun `listTemplateJsons - URI wird korrekt gebaut (MockK)`() {
        val uriBuilder = mockk<UriBuilder>(relaxed = true)

        every { uriBuilder.pathSegment(any(), any(), any(), any()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<String>()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<Int>()) } returns uriBuilder
        every { uriBuilder.build() } returns URI("http://example.com")

        every { gl.getFlux(clazz = GitLabTreeItemDto::class.java, query = any()) } answers {
            val fn = thirdArg<(UriBuilder) -> Any>()

            fn.invoke(uriBuilder)

            verify {
                uriBuilder.pathSegment(
                    "projects",
                    projectId,
                    "repository",
                    "tree",
                )
            }

            verify { uriBuilder.queryParam("ref", branch) }
            verify { uriBuilder.queryParam("path", "$version/input/templates") }
            verify { uriBuilder.queryParam("per_page", 2000) }

            Flux.empty()
        }

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "listTemplateJsons",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        (method.invoke(fetcher, projectId, branch, version) as Mono<*>).block()
    }

    @Test
    fun `fetchOpenMergeRequestRefOpt - returns MR`() {
        val clazz = Class.forName("${DetailFetcher::class.java.name}\$GitLabMergeRequest")
        val ctor =
            clazz.getDeclaredConstructor(
                Int::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
            )
        ctor.isAccessible = true
        val raw = ctor.newInstance(55, "http://mr", branch, "opened")

        @Suppress("UNCHECKED_CAST")
        every { gl.getFlux(clazz = clazz as Class<Any>, query = any()) } returns Flux.just(raw)

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "fetchOpenMergeRequestRefOpt",
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val opt =
            (method.invoke(fetcher, projectId, branch) as Mono<java.util.Optional<MergeRequestRef>>)
                .block()!!

        assertThat(opt).isPresent
        assertThat(opt.get().id).isEqualTo(55)
    }

    @Test
    fun `fetchOpenMergeRequestRefOpt - no MRs return empty optional`() {
        val clazz = Class.forName("${DetailFetcher::class.java.name}\$GitLabMergeRequest")

        every { gl.getFlux(clazz = clazz as Class<Any>, query = any()) } returns Flux.empty()

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "fetchOpenMergeRequestRefOpt",
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        val opt = (method.invoke(fetcher, projectId, branch) as Mono<Optional<MergeRequestRef>>).block()!!
        assertThat(opt).isEmpty
    }

    @Test
    fun `fetchOpenMergeRequestRefOpt - 404 Error return empty optional`() {
        val clazz = Class.forName("${DetailFetcher::class.java.name}\$GitLabMergeRequest")

        every {
            gl.getFlux(clazz = clazz as Class<Any>, query = any())
        } returns Flux.error(GitLabHttpException(404, "404"))

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "fetchOpenMergeRequestRefOpt",
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        val opt = (method.invoke(fetcher, projectId, branch) as Mono<Optional<MergeRequestRef>>).block()!!
        assertThat(opt).isEmpty
    }

    @Test
    fun `fetchOpenMergeRequestRefOpt - non-404 Error wird propagiert`() {
        val clazz = Class.forName("${DetailFetcher::class.java.name}\$GitLabMergeRequest")

        every {
            gl.getFlux(clazz = clazz as Class<Any>, query = any())
        } returns Flux.error(GitLabHttpException(500, "500"))

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "fetchOpenMergeRequestRefOpt",
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        assertThatThrownBy {
            @Suppress("UNCHECKED_CAST")
            (method.invoke(fetcher, projectId, branch) as Mono<Optional<MergeRequestRef>>).block()
        }.isInstanceOf(GitLabHttpException::class.java)
    }

    @Test
    fun `fetchOpenMergeRequestRefOpt - URI wird korrekt gebaut (MockK)`() {
        val uriBuilder = mockk<UriBuilder>(relaxed = true)

        every { uriBuilder.pathSegment(any(), any(), any()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<String>()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<Int>()) } returns uriBuilder
        every { uriBuilder.build() } returns URI("http://example.com")

        val mrClazz = Class.forName("${DetailFetcher::class.java.name}\$GitLabMergeRequest")

        @Suppress("UNCHECKED_CAST")
        every { gl.getFlux(clazz = mrClazz as Class<Any>, query = any()) } answers {
            val fn = thirdArg<(UriBuilder) -> Any>()
            fn.invoke(uriBuilder)

            verify {
                uriBuilder.pathSegment("projects", projectId, "merge_requests")
            }
            verify { uriBuilder.queryParam("source_branch", branch) }
            verify { uriBuilder.queryParam("state", "opened") }
            verify { uriBuilder.queryParam("per_page", 1) }

            Flux.empty()
        }

        val method =
            DetailFetcher::class.java
                .getDeclaredMethod(
                    "fetchOpenMergeRequestRefOpt",
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        (method.invoke(fetcher, projectId, branch) as Mono<*>).block()
    }
}
