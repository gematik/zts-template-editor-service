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
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.GitLabHttpException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.GitLabNotFoundException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.GitLabServerErrorException
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabMergeRequestDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Flux
import java.net.URI

class GitLabMergeRequestLookupTest {
    private lateinit var gl: GitLabClient
    private lateinit var lookup: GitLabMergeRequestLookup

    private val projectId = "123"
    private val branch = "feature/test-branch"

    @BeforeEach
    fun setup() {
        gl = mockk()
        lookup = GitLabMergeRequestLookup(gl)
    }

    @Test
    fun `findOpenForBranch - returns merge request ref`() {
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns
            Flux.just(
                GitLabMergeRequestDto(
                    iid = 55,
                    webUrl = "https://gitlab.example.com/project/-/merge_requests/55",
                    sourceBranch = "source-branch",
                    targetBranch = "target-branch",
                    projectId = "123456",
                ),
            )

        val result = lookup.findOpenForBranch(projectId, branch).block()!!

        assertThat(result).isEqualTo(
            MergeRequestRef(
                id = 55,
                webUrl = "https://gitlab.example.com/project/-/merge_requests/55",
            ),
        )
    }

    @Test
    fun `findOpenForBranch - empty flux returns empty mono`() {
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns Flux.empty()

        val result = lookup.findOpenForBranch(projectId, branch).block()

        assertThat(result).isNull()
    }

    @Test
    fun `findOpenForBranch - GitLabNotFoundException returns empty mono`() {
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns
            Flux.error(GitLabNotFoundException())

        val result = lookup.findOpenForBranch(projectId, branch).block()

        assertThat(result).isNull()
    }

    @Test
    fun `findOpenForBranch - non not found exception is propagated`() {
        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } returns
            Flux.error(GitLabHttpException(500, "boom"))

        assertThatThrownBy {
            lookup.findOpenForBranch(projectId, branch).block()
        }.isInstanceOf(GitLabServerErrorException::class.java)
    }

    @Test
    fun `findOpenForBranch - URI is built correctly`() {
        val uriBuilder = mockk<UriBuilder>(relaxed = true)

        every { uriBuilder.pathSegment(any(), any(), any()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<String>()) } returns uriBuilder
        every { uriBuilder.queryParam(any<String>(), any<Int>()) } returns uriBuilder
        every { uriBuilder.build() } returns URI("http://example.com")

        every { gl.getFlux(clazz = GitLabMergeRequestDto::class.java, query = any()) } answers {
            val fn = thirdArg<(UriBuilder) -> Any>()
            fn.invoke(uriBuilder)

            verify {
                uriBuilder.pathSegment("projects", projectId, "merge_requests")
            }
            verify { uriBuilder.queryParam("state", "opened") }
            verify { uriBuilder.queryParam("source_branch", branch) }
            verify { uriBuilder.queryParam("per_page", 1) }

            Flux.empty()
        }

        lookup.findOpenForBranch(projectId, branch).block()
    }
}
