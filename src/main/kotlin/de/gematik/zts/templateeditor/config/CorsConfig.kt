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

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {
    /** Configure CORS settings for the application.
     *
     * This configuration allows cross-origin requests from the frontend URL specified
     * in the CorsProperties. It permits GET, POST, and DELETE methods and allows all headers.
     * Credentials are also allowed in the requests.
     *
     * @param corsProperties The Cors properties containing the frontend URL.
     * @return A CorsWebFilter configured with the specified CORS settings.
     */
    @Bean
    fun corsFilter(corsProperties: CorsProperties): CorsWebFilter {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "DELETE")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val corsConfigurationSource = UrlBasedCorsConfigurationSource()
        corsConfigurationSource.registerCorsConfiguration("/**", configuration)

        return CorsWebFilter(corsConfigurationSource)
    }
}
