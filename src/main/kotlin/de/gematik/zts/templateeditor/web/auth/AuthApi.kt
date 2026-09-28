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

package de.gematik.zts.templateeditor.web.auth

import de.gematik.zts.templateeditor.config.openapi.BearerAuth
import de.gematik.zts.templateeditor.config.openapi.StandardApiErrors
import de.gematik.zts.templateeditor.domain.auth.UserDetailsSub
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import reactor.core.publisher.Mono

interface AuthApi {
    @Operation(
        summary = "Get current user information",
        description = "Returns information about the currently authenticated user, including backend reviewer mapping.",
    )
    @BearerAuth
    @StandardApiErrors
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "User information successfully resolved",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = UserDetailsSub::class),
                    ),
                ],
            ),
        ],
    )
    fun userinfo(): Mono<UserDetailsSub>
}
