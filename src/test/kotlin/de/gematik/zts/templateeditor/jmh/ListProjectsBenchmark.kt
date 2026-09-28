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
import de.gematik.zts.templateeditor.domain.projects.ProjectItem
import de.gematik.zts.templateeditor.gitlab.ProjectsAdapter
import io.mockk.clearAllMocks
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
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
// for parallel Benchmark
//@Threads(2)
//@Threads(4)
open class ListProjectsBenchmark {
    @Param("1", "2", "10", "100", "500")
    var projectCount: Int = 0

    private lateinit var context: ConfigurableApplicationContext
    private lateinit var webTestClient: WebTestClient
    private lateinit var projectsAdapter: ProjectsAdapter

    @TestConfiguration
    class MockConfig {
        @Bean
        @Primary
        fun mockProjectsAdapter(): ProjectsAdapter = mockk(relaxed = true)
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
            .build()

        projectsAdapter = context.getBean(ProjectsAdapter::class.java)
    }

    @Setup(Level.Iteration)
    fun setupMocks() {
        clearAllMocks()
        every {
            projectsAdapter.list(anyNullable(), any(), any(), any())
        } returns Flux.fromIterable(buildProjects(projectCount))
    }

    @TearDown(Level.Trial)
    fun stopContext() {
        context.close()
    }

    @Benchmark
    fun benchmarkListProjects(): Int {
        val result = webTestClient.get()
            .uri("/projects?sort=title")
            .headers { it.setBearerAuth("test-access-token") }
            .exchange()
            .returnResult(ProjectItem::class.java)
            .responseBody
            .collectList()
            .block()!!

        return result.size
    }

    private fun buildProjects(count: Int): List<ProjectItem> =
        (1..count).map { i ->
            ProjectItem(
                projectId = i.toLong(),
                title = "Gematik App $i",
                lastModified = OffsetDateTime.parse("2025-10-20T12:00:00Z"),
                description = "A Project",
            )
        }
}