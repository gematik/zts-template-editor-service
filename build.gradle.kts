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

import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.jmh)
    application
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.boot.openapi)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jacoco)
}

group = "de.gematik.zts"
version = "1.0.5-b103"
description = "template-editor-service"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        extraWarnings.set(true)
    }

    sourceSets.named("test") {
        kotlin.exclude("**/jmh/**")
    }

    sourceSets.named("jmh") {
        kotlin.srcDir("src/test/kotlin/de/gematik/zts/templateeditor/jmh")
    }
}
sourceSets {
    named("jmh") {
        resources.srcDir("src/test/resources")
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass = "de.gematik.zts.templateeditor.TemplateEditorApplicationKt"
}

configurations.named("jmhImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("jmhRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation(libs.bundles.spring.boot)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.jackson)

    implementation(libs.bundles.patches)

    implementation(libs.kotlin.stdlib)
    implementation(libs.micromenter.registry.prometheus)

    runtimeOnly(libs.codehaus.janino)

    testImplementation(libs.bundles.test.junit5.kotest)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webflux.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.assertj.core)

    add("jmh", libs.jmh.core)
    add("kaptJmh", libs.jmh.generator.annprocess)
}

tasks.getByName<BootJar>("bootJar") {
    enabled = true
    archiveFileName.set("template-editor.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<me.champeau.jmh.JMHTask>("jmh") {
    description = "Run jmh benchmarks"
    resultFormat.set("JSON")
    resultsFile.set(project.layout.buildDirectory.file("reports/jmh/results.json").get().asFile)
    profilers.set(listOf("gc", "stack"))
    fork = 1
    jvmArgsAppend.set(
        listOf(
            "-Xmx1g",
            "-XX:StartFlightRecording=filename=${layout.buildDirectory.file("reports/jmh/results.jfr").get().asFile.absolutePath},settings=profile,dumponexit=true"
        )
    )
}

tasks.test {
    useJUnitPlatform()

    exclude("**/jmh/**")

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    reports { xml.required.set(true) }
}

openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.yaml")
}
