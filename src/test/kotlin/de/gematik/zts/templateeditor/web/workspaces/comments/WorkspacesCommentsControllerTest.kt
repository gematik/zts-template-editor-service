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

package de.gematik.zts.templateeditor.web.workspaces.comments

import de.gematik.zts.templateeditor.domain.reviews.CommentCreateRequest
import de.gematik.zts.templateeditor.domain.reviews.CommentCreateResponse
import de.gematik.zts.templateeditor.domain.reviews.CommentsPayload
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.gitlab.reviews.ReviewsService
import de.gematik.zts.templateeditor.web.AuthTestSupport
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WorkspacesCommentsControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var reviewsService: ReviewsService
    @Autowired lateinit var authTestSupport: AuthTestSupport

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockReviewsService(): ReviewsService = mockk(relaxed = true)

        @Bean
        fun authTestSupport() = AuthTestSupport()
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    // -------- GET /api/workspaces/comments --------

    @Test
    fun `getComments without auth returns 401 and does not call port`() {
        every { reviewsService.listComments(any(), any(), any(), any()) } returns Mono.never()

        webTestClient.get()
            .uri("/api/workspaces/comments?repositoryId=r&mrId=1&branch=b&version=1.0.0")
            .exchange()
            .expectStatus().isUnauthorized

        verify { reviewsService wasNot Called }
    }

    @Test
    fun `getComments missing required params returns 400`() {
        every { reviewsService.listComments(any(), any(), any(), any()) } returns Mono.never()

        webTestClient.get()
            .uri("/workspaces/comments?mrId=1&branch=b&version=1.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.get()
            .uri("/workspaces/comments?repositoryId=r&branch=b&version=1.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.get()
            .uri("/workspaces/comments?repositoryId=r&mrId=1&version=1.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest

        webTestClient.get()
            .uri("/workspaces/comments?repositoryId=r&mrId=1&branch=b")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest

        verify { reviewsService wasNot Called }
    }

    @Test
    fun `getComments maps required and optional params`() {
        val payload = mockk<CommentsPayload>(relaxed = true)
        every { reviewsService.listComments(any(), any(), any(), any()) } returns Mono.just(payload)

        webTestClient.get()
            .uri("/workspaces/comments?repositoryId=1234&mrId=1&branch=feature-x&version=2.0.0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk

        verify {
            reviewsService.listComments(
                repo = "1234",
                mrId = "1",
                branch = "feature-x",
                version = "2.0.0"
            )
        }
    }

    @Test
    fun `createComment returns 201 and forwards body`() {
        val resp = CommentCreateResponse("123")
        every { reviewsService.createComment(any<CommentCreateRequest>()) } returns Mono.just(resp)

        val json = """
            {
                "repositoryId": "12345",
                "branch": "feature/add-comments",
                "mrId": "1",
                "version": "1.4.2",
                "type": "template",
                "fileName": "main.json",
                "line": 128,
                "body": "Okay?"
            }
        """.trimIndent()

        webTestClient.post()
            .uri("/workspaces/comments")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectStatus().isCreated

        verify { reviewsService.createComment(any<CommentCreateRequest>()) }
    }

    @Test
    fun `reply returns 201 and maps fields`() {
        val resp = mockk<CommentCreateResponse>(relaxed = true)
        every { reviewsService.reply(any(), any(), any(), any(), any()) } returns Mono.just(resp)

        val json = """
            {
              "repositoryId": "12345",
              "mrId": 42,
              "threadId": "321",
              "body": "ack",
              "resolved": true
            }
        """.trimIndent()

        webTestClient.post()
            .uri("/workspaces/comments/reply")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(json)
            .exchange()
            .expectStatus().isCreated

        verify { reviewsService.reply("12345", "42", "321", "ack", true) }
    }

    @Test
    fun `serialize and deserialize full payload`() {
        val req = CommentCreateRequest(
            repositoryId = "zts-template-repo",
            branch = "feature/add-comments",
            mrId = "1",
            type = CommitChange.Type.TEMPLATE,
            version = "1.2.3",
            fileName = "header.mustache",
            line = 17,
            body = "Bitte Variable X dokumentieren."
        )

        val mapper = ObjectMapper()

        val json = mapper.writeValueAsString(req)
        assertThat(json).contains(
            "\"repositoryId\":\"zts-template-repo\"",
            "\"branch\":\"feature/add-comments\"",
            "\"mrId\":\"1\",\"version\":\"1.2.3\"",
            "\"type\":\"template\"",
            "\"fileName\":\"header.mustache\"",
            "\"line\":17",
            "\"body\":\"Bitte Variable X dokumentieren.\""
        )

        val parsed = mapper.readValue<CommentCreateRequest>(json)
        assertThat(parsed).isEqualTo(req)
    }
}
