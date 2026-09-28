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

package de.gematik.zts.templateeditor.config

import de.gematik.zts.templateeditor.logging.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Rejects non-multipart POST/PUT requests whose Content-Length exceeds the allowed limit.
 *
 * This provides early rejection *before* the body is deserialized into heap, preventing
 * oversized JSON payloads from consuming excessive memory. Multipart uploads are excluded
 * because they have their own size handling (streamed to temp files via the multipart codec).
 *
 * The limit matches the lowered `spring.http.codecs.max-in-memory-size` (10 MB).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class PayloadSizeLimitFilter(
    @param:Value("\${validation.commit.max.inmemory.max.size}") private val maxInMemorySize: Long,
) : WebFilter {
    private val log by logger()

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val method = request.method

        // Only inspect methods that carry a body
        if (method != HttpMethod.POST && method != HttpMethod.PUT) {
            return chain.filter(exchange)
        }

        // Multipart uploads are handled separately (streamed to disk)
        val contentType = request.headers.contentType
        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            return chain.filter(exchange)
        }

        // Reject based on Content-Length header (if present)
        val contentLength = request.headers.contentLength
        if (contentLength > maxInMemorySize) {
            log.warn(
                "Rejecting oversized request: Content-Length={} exceeds limit={} for {} {}",
                contentLength,
                maxInMemorySize,
                method,
                request.path,
            )
            exchange.response.statusCode = HttpStatus.PAYLOAD_TOO_LARGE
            return exchange.response.setComplete()
        }

        return chain.filter(exchange)
    }
}

