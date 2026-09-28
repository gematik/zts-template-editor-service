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

package de.gematik.zts.templateeditor.gitlab

import de.gematik.zts.templateeditor.gitlab.exceptions.http.client.*
import de.gematik.zts.templateeditor.gitlab.exceptions.http.server.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

class MapGitLabErrorsTest {

    private fun wcre(status: Int): WebClientResponseException =
        WebClientResponseException.create(
            status,
            "status-$status",
            HttpHeaders.EMPTY,
            ByteArray(0),
            StandardCharsets.UTF_8
        )

    // Falls dein Konstruktor anders ist: hier anpassen.
    private fun glhe(status: Int): GitLabHttpException =
        GitLabHttpException(status = status, "message")


    @Test
    fun `Mono - GitLabHttpException 404 maps to GitLabNotFoundException`() {
        val ex = assertThrows(GitLabNotFoundException::class.java) {
            Mono.error<String>(glhe(404))
                .mapGitLabErrors()
                .block()
        }
        assertNotNull(ex)
    }

    @Test
    fun `Mono - GitLabHttpException 400 maps to GitLabBadRequestException`() {
        val ex = assertThrows(GitLabBadRequestException::class.java) {
            Mono.error<String>(glhe(400))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("GitLab API returned HTTP 400 - Invalid request payload.", ex.message)
        assertEquals("Die Anfrage ist ungültig. Bitte überprüfen Sie Ihre Eingaben und versuchen Sie es erneut.", ex.toApiMessage())
    }

    @Test
    fun `Mono - GitLabHttpException 403 maps to GitLabForbiddenException`(){
        val ex = assertThrows(GitLabForbiddenException::class.java) {
            Mono.error<String>(glhe(403))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("GitLab API returned HTTP 403 - Access denied.", ex.message)
        assertEquals("Sie haben keine Berechtigung, auf angefragte GitLab-Ressource zuzugreifen.", ex.toApiMessage())
    }

    @Test
    fun `Mono - GitLabHttpException 500 maps to GitLabServerErrorException`() {
        val ex = assertThrows(GitLabServerErrorException::class.java) {
            Mono.error<String>(glhe(500))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("GitLab API returned HTTP 500 - Internal server error.", ex.message)
        assertEquals("Beim GitLab-Server ist ein interner Fehler aufgetreten.\n" +
                "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.", ex.toApiMessage())
    }

    @Test
    fun `Mono - GitLabHttpException 503 maps to  GitLabServiceUnavailableException`() {
        val ex = assertThrows( GitLabServiceUnavailableException::class.java) {

            Mono.error<String>(glhe(503))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("GitLab API returned HTTP 503 - Service unavailable.", ex.message)
        assertEquals("Der GitLab-Service ist derzeit nicht verfügbar.\n"+
            "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.", ex.toApiMessage())
    }

    @Test
    fun `Mono - WebClientResponseException 404 maps to GitLabNotFoundException`() {
        val ex = assertThrows(GitLabNotFoundException::class.java) {
            Mono.error<String>(wcre(404))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("GitLab API returned HTTP 404 - Resource not found.", ex.message)
        assertEquals("Die angeforderte GitLab-Ressource wurde nicht gefunden.\n"+
        "Versuchen Sie es später erneut oder wenden sie sich an den Servicebetreiber.", ex.toApiMessage())
    }

    @Test
    fun `Mono - WebClientResponseException 418 maps to GitLabUnhandledClietException 4xx`() {
        val ex = assertThrows(GitLabUnhandledClientException::class.java) {
            Mono.error<String>(wcre(418))
                .mapGitLabErrors()
                .block()
        }
        assertEquals("Unhandled 418 error while requesting GitLab API", ex.message)
        assertEquals("Die Anfrage konnte aufgrund eines Client-Fehlers nicht verarbeitet werden.\n"+
                "Bitten wenden Sie sich an den Servicebetreiber.", ex.toApiMessage())
    }

    @Test
    fun `Mono - other exception is not mapped`() {
        val original = IllegalStateException("boom")

        val ex = assertThrows(IllegalStateException::class.java) {
            Mono.error<String>(original)
                .mapGitLabErrors()
                .block()
        }

        assertSame(original, ex)
    }


    @Test
    fun `Flux - GitLabHttpException 404 maps to GitLabNotFoundException`() {
        val ex = assertThrows(GitLabNotFoundException::class.java) {
            Flux.error<String>(glhe(404))
                .mapGitLabErrors()
                .collectList()
                .block()
        }
        assertEquals("GitLab API returned HTTP 404 - Resource not found.", ex.message)
        assertEquals("Die angeforderte GitLab-Ressource wurde nicht gefunden.\n" +
                "Versuchen Sie es später erneut oder wenden sie sich an den Servicebetreiber.", ex.toApiMessage())

    }

    @Test
    fun `Flux - GitLabHttpException 401 maps to GitLabUnauthorizedException`() {
        val ex = assertThrows(GitLabUnauthorizedException::class.java) {
            Flux.error<String>(glhe(401))
                .mapGitLabErrors()
                .collectList()
                .block()
        }
        assertEquals("GitLab API returned HTTP 401 - Authentication failed or missing.", ex.message)
        assertEquals("Sie sind nicht angemeldet oder Ihre Sitzung ist abgelaufen. Bitte melden Sie sich erneut an.", ex.toApiMessage())
    }

    @Test
    fun `Flux - GitLabHttpException 409 maps to GitLabConflictException`() {
        val ex = assertThrows(GitLabConflictException::class.java) {
            Flux.error<String>(glhe(409))
                .mapGitLabErrors()
                .collectList()
                .block()
        }
        assertEquals("GitLab API returned HTTP 409 - Resource conflict, it could already exist.", ex.message)
        assertEquals("Die Aktion konnte nicht durchgeführt werden, da ein Konflikt besteht (z. B. existiert das Objekt bereits).", ex.toApiMessage())
    }

    @Test
    fun `Flux - GitLabHttpException 502 maps to UpstreamException Upstream error`() {
        val ex = assertThrows(GitLabUpstreamException::class.java) {
            Flux.error<String>(glhe(502))
                .mapGitLabErrors()
                .collectList()
                .block()
        }
        assertEquals("GitLab returned 502 - upstream error.", ex.message)
        assertEquals("Der GitLab-Dienst ist derzeit nicht erreichbar oder reagiert fehlerhaft.\n"+
                "Bitte versuchen Sie es später erneut oder wenden Sie sich an den Servicebetreiber.", ex.toApiMessage())

    }

    @Test
    fun `Flux - WebClientResponseException 404 maps to NotFoundException`() {
        assertThrows(GitLabNotFoundException::class.java) {
            Flux.error<String>(wcre(404))
                .mapGitLabErrors()
                .collectList()
                .block()
        }
    }

    @Test
    fun `Flux - other exception is not mapped`() {
        val original = RuntimeException("x")

        val ex = assertThrows(RuntimeException::class.java) {
            Flux.error<String>(original)
                .mapGitLabErrors()
                .collectList()
                .block()
        }

        assertSame(original, ex)
    }
}
