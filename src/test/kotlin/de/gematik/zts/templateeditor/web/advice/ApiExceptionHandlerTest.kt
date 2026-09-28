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

package de.gematik.zts.templateeditor.web.advice

import de.gematik.zts.templateeditor.domain.exception.ForbiddenException
import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.*
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

class ApiExceptionHandlerTest {

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setup() {
        client = WebTestClient
            .bindToController(TestThrowingController())
            .controllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `ServerWebInputException - 400 Invalid request`() {
        client.get().uri("/test/input?count=nope")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("BAD_REQUEST")
            .jsonPath("$.message").isEqualTo("Die Anfrage ist ungültig. Bitte überprüfen Sie Ihre Eingaben und versuchen Sie es erneut.")
    }

    @Test
    fun `IllegalArgumentException - 400 Invalid request`() {
        client.get().uri("/test/illegal-arg")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("BAD_REQUEST")
            .jsonPath("$.message").isEqualTo("Die Anfrage ist ungültig. Bitte überprüfen Sie Ihre Eingaben und versuchen Sie es erneut.")
    }

    @Test
    fun `GitLabNotFoundException - 404 Not found`() {
        client.get().uri("/test/gitlab/not-found")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.code").isEqualTo("NOT_FOUND")
            .jsonPath("$.message").isEqualTo("Die angeforderte GitLab-Ressource wurde nicht gefunden.\n" +
                    "Versuchen Sie es später erneut oder wenden sie sich an den Servicebetreiber.")
    }

    @Test
    fun `GitLabUpstreamException - 502 Upstream service error`() {
        client.get().uri("/test/gitlab/upstream")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.code").isEqualTo("BAD_GATEWAY")
            .jsonPath("$.message").isEqualTo("Der GitLab-Dienst ist derzeit nicht erreichbar oder reagiert fehlerhaft.\n" +
                    "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.")
    }

    @Test
    fun `GitLabBadRequestException - 400 Bad request`() {
        client.get().uri("/test/gitlab/bad-request")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("BAD_REQUEST")
            .jsonPath("$.message").isEqualTo(
                "Die Anfrage ist ungültig. Bitte überprüfen Sie Ihre Eingaben und versuchen Sie es erneut."
            )
    }

    @Test
    fun `GitLabConflictException - 409 Conflict`() {
        client.get().uri("/test/gitlab/conflict")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("CONFLICT")
            .jsonPath("$.message").isEqualTo(
                "Die Aktion konnte nicht durchgeführt werden, da ein Konflikt besteht (z. B. existiert das Objekt bereits)."
            )
    }

    @Test
    fun `GitLabForbiddenException - 403 Forbidden`() {
        client.get().uri("/test/gitlab/forbidden")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.code").isEqualTo("FORBIDDEN")
            .jsonPath("$.message").isEqualTo(
                "Sie haben keine Berechtigung, auf angefragte GitLab-Ressource zuzugreifen."
            )
    }

    @Test
    fun `GitLabUnauthorizedException - 401 Unauthorized`() {
        client.get().uri("/test/gitlab/unauthorized")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo(
                "Sie sind nicht angemeldet oder Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an."
            )
    }

    @Test
    fun `ForbiddenException - 401 Unauthorized`() {
        client.get().uri("/test/forbidden")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo(
                "Sie sind nicht autorisiert, diese Ressource zuzugreifen. Bitte melden Sie sich an oder überprüfen Sie Ihre Berechtigungen."
            )
    }

    @Test
    fun `GitLabUnhandledClientException - 400 Client error`() {
        client.get().uri("/test/gitlab/unhandled")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNHANDLED_CLIENT")
            .jsonPath("$.message").isEqualTo(
                "Die Anfrage konnte aufgrund eines Client-Fehlers nicht verarbeitet werden.\n" +
                        "Bitten wenden Sie sich an den Servicebetreiber."
            )
    }

    @Test
    fun `GitLabServerErrorException - 500 server error`() {
        client.get().uri("/test/gitlab/server-error")
            .exchange()
            .expectStatus().isEqualTo(500)
            .expectBody()
            .jsonPath("$.code").isEqualTo("SERVER_ERROR")
            .jsonPath("$.message").isEqualTo(
                "Beim GitLab-Server ist ein interner Fehler aufgetreten.\n" +
                        "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber."
            )
    }

    @Test
    fun `GitLabServiceUnavailableException - 503 Service unavailable`() {
        client.get().uri("/test/gitlab/unavailable")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
            .jsonPath("$.message").isEqualTo(
                "Der GitLab-Service ist derzeit nicht verfügbar.\n" +
                        "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber."
            )
    }

    @Test
    fun `Unhandled Exception - 500 Internal server error`() {
        client.get().uri("/test/boom")
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
            .jsonPath("$.message").isEqualTo("Beim Verarbeiten der Anfrage ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es später erneut oder kontaktieren Sie den Servicebetreiber.")
    }

    // --- test controller ---

    @RestController
    @Validated
    class TestThrowingController {

        @GetMapping("/test/constraint")
        fun constraint(@RequestParam @Size(min = 3, message = "too short") value: String) = "ok"

        data class BindBody(val name: String?)

        @PostMapping("/test/bind")
        fun bind(@Valid @RequestBody body: BindBody) = "ok"

        @GetMapping("/test/input")
        fun input(@RequestParam count: Int) = "ok"

        @GetMapping("/test/illegal-arg")
        fun illegalArg(): String = throw IllegalArgumentException("bad")

        @GetMapping("/test/boom")
        fun boom(): String = throw RuntimeException("boom")

        @GetMapping("/test/forbidden")
        fun forbiddenCustom(): String = throw ForbiddenException("forbidden")

        // Endpoints for gitlab forwarding

        @GetMapping("/test/gitlab/not-found")
        fun notFound(): String = throw GitLabNotFoundException()

        @GetMapping("/test/gitlab/upstream")
        fun upstream(): String = throw GitLabUpstreamException(message = "Upstream 5xx")

        @GetMapping("/test/gitlab/bad-request")
        fun badRequest(): String = throw GitLabBadRequestException()

        @GetMapping("/test/gitlab/conflict")
        fun conflict(): String = throw GitLabConflictException()

        @GetMapping("/test/gitlab/forbidden")
        fun forbidden(): String = throw GitLabForbiddenException()

        @GetMapping("/test/gitlab/unauthorized")
        fun unauthorized(): String = throw GitLabUnauthorizedException()

        @GetMapping("/test/gitlab/unhandled")
        fun unhandled(): String = throw GitLabUnhandledClientException()

        @GetMapping("/test/gitlab/server-error")
        fun serverError(): String = throw GitLabServerErrorException()

        @GetMapping("/test/gitlab/unavailable")
        fun unavailable(): String = throw GitLabServiceUnavailableException()

    }
}
