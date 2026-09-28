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

package de.gematik.zts.templateeditor

import de.gematik.zts.templateeditor.config.CorsProperties
import de.gematik.zts.templateeditor.config.GitLabProperties
import de.gematik.zts.templateeditor.config.HttpProperties
import de.gematik.zts.templateeditor.gitlab.validation.CommitContentProperties
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
    info =
        Info(
            title = "Template Editor Service",
            version = "v1",
        ),
    security = [
        SecurityRequirement(name = "bearerAuth"),
    ],
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "GitLab access token",
    `in` = SecuritySchemeIn.HEADER,
)
@EnableConfigurationProperties(
    CorsProperties::class,
    GitLabProperties::class,
    HttpProperties::class,
    CommitContentProperties::class,
)
class TemplateEditorApplication

fun main(args: Array<String>) {
    SpringApplication.run(TemplateEditorApplication::class.java, *args)
}
