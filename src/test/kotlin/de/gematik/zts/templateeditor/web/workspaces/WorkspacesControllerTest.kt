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

package de.gematik.zts.templateeditor.web.workspaces

import de.gematik.zts.templateeditor.domain.workspaces.*
import de.gematik.zts.templateeditor.gitlab.WorkspacesAdapter
import de.gematik.zts.templateeditor.web.AuthTestSupport
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WorkspacesControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var workspacesAdapter: WorkspacesAdapter
    @Autowired lateinit var authTestSupport: AuthTestSupport

    @TestConfiguration
    class MockConfig {
        @Bean @Primary
        fun mockAdapter(): WorkspacesAdapter = mockk(relaxed = true)

        @Bean
        fun authSupport() = AuthTestSupport()
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `listBranches returns branches`() {
        val b1 = BranchItem("dev", OffsetDateTime.now(), "me")
        every { workspacesAdapter.listBranches("123") } returns Flux.just(b1)

        webTestClient.get()
            .uri("/workspaces?repositoryId=123")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].branch").isEqualTo("dev")

        verify(exactly = 1) { workspacesAdapter.listBranches("123") }
    }

    @Test
    fun `details returns workspace`() {
        val details = WorkspaceDetails(
            templateJsons = emptyList(),
            templateMds = emptyList(),
            packageTemplateJson = "{}",
            metadataJson = "{}",
            changelogsJson = "",
            externalSourcesMd = "",
            fhirConversionNotesMd = "",
            noteOnAuthorMd = "",
            notesOnUpdateCyclesMd = "",
            descriptionGenericMd = "",
            downloadConditionsXml = "",
            inputFileList = emptyList(),
            mergeRequestRef = MergeRequestRef(5, "http://mr")
        )

        every { workspacesAdapter.details("123","dev","2025.0.0") } returns Mono.just(details)

        webTestClient.get()
            .uri("/workspaces/details?repositoryId=123&branch=dev&version=2025.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk

        verify(exactly = 1) { workspacesAdapter.details("123","dev","2025.0.0") }
    }

    @Test
    fun `openByMR returns details`() {
        val details = mockk<WorkspaceDetails>(relaxed = true)
        every { workspacesAdapter.openByMr("123", 99, "2025.0.0") } returns Mono.just(details)

        webTestClient.get()
            .uri("/workspaces/openByMR?projectId=123&mrId=99&version=2025.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `commit validation failed empty changes`() {
        val resp = CommitResponse("abc","dev", MergeRequestRef(10,"http://mr"))
        every { workspacesAdapter.commit(any()) } returns Mono.just(resp)

        val json = """
          {
            "repositoryId":"123",
            "branch":"dev",
            "version":"2025.0.0",
            "message":"msg",
            "changes":[],
            "createMergeRequest":true
          }
        """.trimIndent()

        webTestClient.post()
            .uri("/workspaces/commit")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectStatus().isBadRequest

        verify(exactly = 0) { workspacesAdapter.commit(any()) }
    }

    @Test
    fun `commit forwards to adapter`() {
        val resp = CommitResponse("abc", "dev", MergeRequestRef(10, "http://mr"))
        every { workspacesAdapter.commit(any()) } returns Mono.just(resp)

        val json = """
      {
        "repositoryId":"123",
        "branch":"dev",
        "version":"2025.0.0",
        "message":"msg",
        "changes":[
          {
            "action":"update",
            "type":"template",
            "fileName":"input/templates/main.template.json",
            "content":"{}",
            "encoding":"text"
          }
        ],
        "createMergeRequest":true
      }
    """.trimIndent()

        webTestClient.post()
            .uri("/workspaces/commit")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectStatus().isOk

        verify(exactly = 1) { workspacesAdapter.commit(any()) }
    }

    @Test
    fun `commitFile forwards multipart to adapter`() {
        val resp = CommitResponse("abc", "dev", MergeRequestRef(10, "http://mr"))
        every { workspacesAdapter.commitFile(any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(resp)

        val builder = MultipartBodyBuilder()
        builder.part("repositoryId", "123")
        builder.part("branch", "dev")
        builder.part("version", "2025.0.0")
        builder.part("message", "msg")
        builder.part("action", "update")
        builder.part("fileName", "main.template.json")

        val fileBytes = """{"hello":"world"}""".toByteArray()
        val resource = object : ByteArrayResource(fileBytes) {
            override fun getFilename(): String = "main.template.json"
        }
        builder.part("file", resource)
            .contentType(MediaType.APPLICATION_JSON)

        webTestClient.post()
            .uri("/workspaces/commitFile")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.commitId").isEqualTo("abc")

        verify(exactly = 1) {
            workspacesAdapter.commitFile(
                repositoryId = "123",
                branch = "dev",
                version = "2025.0.0",
                message = "msg",
                action = "update",
                file = any(),
                fileName = "main.template.json"
            )
        }
    }

    @Test
    fun `commitFile missing file part returns 400`() {
        val builder = MultipartBodyBuilder()
        builder.part("repositoryId", "123")
        builder.part("branch", "dev")
        builder.part("version", "2025.0.0")
        builder.part("message", "msg")
        builder.part("action", "update")
        builder.part("fileName", "input/templates/main.template.json")

        webTestClient.post()
            .uri("/workspaces/commitFile")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isBadRequest

        verify(exactly = 0) { workspacesAdapter.commitFile(any(), any(), any(), any(), any(), any(), any()) }
    }
}
