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

package de.gematik.zts.templateeditor.jmh

import de.gematik.zts.templateeditor.TemplateEditorApplication
import de.gematik.zts.templateeditor.domain.workspaces.GitLabCommitResp
import de.gematik.zts.templateeditor.gitlab.GitLabClient
import de.gematik.zts.templateeditor.gitlab.mapping.workspace.GitLabBranchDto
import io.mockk.every
import io.mockk.mockk
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
// for parallel Benchmark
//@Threads(2)
//@Threads(4)
open class UploadBenchmark {

    @Param("1", "2", "5", "10")
    var fileCount: Int = 0

    private lateinit var context: ConfigurableApplicationContext
    private lateinit var webTestClient: WebTestClient
    private lateinit var uploadResource: ClassPathResource

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockGitLabClient(): GitLabClient = mockk<GitLabClient>().also { gl ->
            // if branch exists then WorkspacesAdapter skips creation
            every { gl.getFlux(clazz = GitLabBranchDto::class.java, query = any()) } returns
                    Flux.just(GitLabBranchDto("dev"))

            every {
                gl.postMonoStreaming(
                    GitLabCommitResp::class.java,
                    any(),
                    any<Flux<DataBuffer>>(),
                )
            } answers {
                thirdArg<Flux<DataBuffer>>()
                    .reduce(0L) { acc, buf ->
                        try {
                            acc + buf.readableByteCount()
                        } finally {
                            DataBufferUtils.release(buf)
                        }
                    }
                    .map { GitLabCommitResp("bench", "bench-short") }
            }
        }
    }

    @Setup(Level.Trial)
    fun startContext() {
        context = SpringApplicationBuilder(
            TemplateEditorApplication::class.java,
            MockConfig::class.java,
        )
            .profiles("jmh")
            .properties("server.port=0")
            .run()

        val port = context.environment.getRequiredProperty("local.server.port")
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(300))
            .build()
        uploadResource = ClassPathResource("Loinc_2.82.zip")
    }

    @Benchmark
    fun benchmarkUpload() {
        repeat(fileCount) {
            val builder = MultipartBodyBuilder()
            builder.part("repositoryId", "123")
            builder.part("branch", "dev")
            builder.part("version", "2025.0.0")
            builder.part("message", "msg")
            builder.part("action", "create")
            builder.part("fileName", "Loinc_2.82.zip")
            builder.part("file", uploadResource, MediaType.APPLICATION_OCTET_STREAM)

            webTestClient.post()
                .uri("/workspaces/commitFile")
                .headers { it.setBearerAuth("test-access-token") }
                .header("X-Forwarded-Groups", "developer")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .returnResult()
        }
    }

    @TearDown(Level.Trial)
    fun stopContext() {
        context.close()
    }
}