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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.core.env.Environment
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ActuatorWebTest {
    @Autowired lateinit var environment: Environment

    private lateinit var mgmt: WebTestClient

    @BeforeEach
    fun setupMgmtClient() {
        val port =
            environment.getProperty("local.management.port")?.toInt()
                ?: environment.getProperty("management.server.port", Int::class.java, 8081)
        mgmt =
            WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:$port")
                .build()
    }

    @Test
    fun `actuator root is available and lists key endpoints`() {
        val body =
            mgmt
                .get()
                .uri("/actuator")
                .exchange()
                .expectStatus()
                .isOk
                .expectHeader()
                .valueMatches("Content-Type", ".*json.*")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        assertThat(body).contains("health")
        assertThat(body).contains("metrics")
        assertThat(body).contains("info")
    }

    @Test
    fun `health returns UP`() {
        mgmt
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueMatches("Content-Type", ".*json.*")
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `metrics endpoint lists common metrics`() {
        val body =
            mgmt
                .get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus()
                .isOk
                .expectHeader()
                .valueMatches("Content-Type", ".*json.*")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        assertThat(body).contains("jvm.memory.used")
        assertThat(body).contains("http.server.requests")
    }

    @Test
    fun `info endpoint reachable (may be empty)`() {
        mgmt
            .get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueMatches("Content-Type", ".*json.*")
    }
}
