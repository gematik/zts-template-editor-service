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


package de.gematik.zts.templateeditor.domain.auth

import de.gematik.zts.templateeditor.domain.exception.ForbiddenException
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestLookup
import de.gematik.zts.templateeditor.domain.workspaces.MergeRequestRef
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class ReviewerAuthorizationTest {
    private lateinit var userInfoService: UserInfoService
    private lateinit var mergeRequestLookup: MergeRequestLookup
    private lateinit var authorization: ReviewerAuthorization

    private val projectId = "123"
    private val branch = "feature/test"

    @BeforeEach
    fun setup() {
        userInfoService = mockk()
        mergeRequestLookup = mockk()
        authorization = ReviewerAuthorization(userInfoService, mergeRequestLookup)
    }

    @Test
    fun `ensureNotReviewer - reviewer gets forbidden`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = true))

        assertThatThrownBy {
            authorization.ensureReviewerState(false).block()
        }.isInstanceOf(ForbiddenException::class.java)
            .hasMessage("Operation not allowed for reviewers")
    }

    @Test
    fun `ensureNotReviewer - non reviewer passes`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = false))

        authorization.ensureReviewerState(false).block()
    }

    @Test
    fun `ensureReviewer - reviewer passes`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = true))

        authorization.ensureReviewerState(true).block()
    }

    @Test
    fun `ensureReviewer - non reviewer gets forbidden`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = false))

        assertThatThrownBy {
            authorization.ensureReviewerState(true).block()
        }.isInstanceOf(ForbiddenException::class.java)
            .hasMessage("User must be reviewer")
    }

    @Test
    fun `ensureReviewerHasOpenMrOrUnauthorized - non reviewer passes without MR lookup`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = false))

        authorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch).block()

        verify(exactly = 0) { mergeRequestLookup.findOpenForBranch(any(), any()) }
    }

    @Test
    fun `ensureReviewerHasOpenMrOrUnauthorized - reviewer with open MR passes`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = true))
        every { mergeRequestLookup.findOpenForBranch(projectId, branch) } returns
            Mono.just(MergeRequestRef(77, "https://gitlab.example/mr/77"))

        authorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch).block()

        verify(exactly = 1) { mergeRequestLookup.findOpenForBranch(projectId, branch) }
    }

    @Test
    fun `ensureReviewerHasOpenMrOrUnauthorized - reviewer without open MR gets forbidden`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = true))
        every { mergeRequestLookup.findOpenForBranch(projectId, branch) } returns Mono.empty()

        assertThatThrownBy {
            authorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch).block()
        }.isInstanceOf(ForbiddenException::class.java)
            .hasMessage("Reviewer requires open merge request")
    }

    @Test
    fun `ensureReviewerHasOpenMrOrUnauthorized - lookup error is propagated`() {
        every { userInfoService.userInfo() } returns Mono.just(user(isReviewer = true))
        every { mergeRequestLookup.findOpenForBranch(projectId, branch) } returns
            Mono.error(IllegalStateException("lookup failed"))

        assertThatThrownBy {
            authorization.ensureReviewerHasOpenMrOrUnauthorized(projectId, branch).block()
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("lookup failed")
    }

    private fun user(isReviewer: Boolean): UserDetailsSub =
        mockk {
            every { this@mockk.isReviewer } returns isReviewer
        }
}
