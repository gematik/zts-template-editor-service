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

package de.gematik.zts.templateeditor.web.reviews

import de.gematik.zts.templateeditor.domain.reviews.CommentItem
import de.gematik.zts.templateeditor.domain.reviews.ReviewsOverviewItem
import de.gematik.zts.templateeditor.domain.workspaces.CommitChange
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import de.gematik.zts.templateeditor.gitlab.reviews.ReviewsService
import de.gematik.zts.templateeditor.web.AuthTestSupport
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ReviewsControllerWebTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var reviewsService: ReviewsService
    @Autowired lateinit var authTestSupport: AuthTestSupport
    @Autowired lateinit var mapper: ObjectMapper

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

    @Test
    fun `get reviews without auth`() {
        every { reviewsService.list(any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/reviews")
            .exchange()
            .expectStatus().isUnauthorized

        verify { reviewsService wasNot Called }
    }

    @Test
    fun `defaults applied`() {
        every { reviewsService.list(any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/reviews")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk

        val stateCaptor = slot<String>()
        val pageCaptor = slot<Int>()
        val sizeCaptor = slot<Int>()

        verify {
            reviewsService.list(
                state = capture(stateCaptor),
                page = capture(pageCaptor),
                size = capture(sizeCaptor)
            )
        }

        assertThat(stateCaptor.captured).isEqualTo("opened")
        assertThat(pageCaptor.captured).isEqualTo(1)
        assertThat(sizeCaptor.captured).isEqualTo(50)
    }

    @Test
    fun `state pass through for allowed values`() {
        every { reviewsService.list(any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/reviews?state=merged")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk

        verify {
            reviewsService.list(
                state = "merged",
                page = 1,
                size = 50
            )
        }
    }

    @Test
    fun `paging invalid returns 400`() {
        every { reviewsService.list(any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/reviews?page=0&pageSize=9999")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")

        webTestClient.get()
            .uri("/reviews?page=-10&pageSize=0")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")

        verify { reviewsService wasNot Called }
    }

    @Test
    fun `state invalid returns 400`() {
        every { reviewsService.list(any(), any(), any()) } returns Flux.empty()

        webTestClient.get()
            .uri("/reviews?state=invalid")
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.details[0].field").isEqualTo("listReviews.state")

        verify { reviewsService wasNot Called }
    }

    @Test
    fun `returns flux from service (smoke with length)`() {
        val i1 = item(1)
        val i2 = item(2)

        every { reviewsService.list(any(), any(), any()) } returns Flux.just(i1, i2)

        webTestClient.get()
            .uri("/reviews?page=1&pageSize=1") // gültig
            .headers { it.setBearerAuth(authTestSupport.bearer()) }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)

        verify { reviewsService.list(any(), any(), any()) }
    }

    private fun item(i: Int) = ReviewsOverviewItem(
        mr = MergeRequestRef(
            id = i,
            webUrl = "https://example.test/$i",
        ),
        projectId = "$i"
    )

    @Test
    fun `serialize and deserialize full CommentItem`() {
        val createdAt = OffsetDateTime.of(2025, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC)
        val item = CommentItem(
            id = "c1",
            fileName = "main.json",
            line = 42,
            type = CommitChange.Type.TEMPLATE,
            body = "Looks good!",
            author = "dev1",
            createdAt = createdAt,
            resolved = true,
            threadId = "t123",
            isInline = true
        )

        val json = mapper.writeValueAsString(item)
        assertThat(json).contains("c1", "Looks good", "2025-01-01T12:30:00Z")

        val parsed = mapper.readValue<CommentItem>(json)
        assertThat(parsed).isEqualTo(item)
    }
}
