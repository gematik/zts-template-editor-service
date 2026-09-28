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

package de.gematik.zts.templateeditor.domain.auth

/**
 * Authentication context created from the headers forwarded by oauth2-proxy.
 *
 * accessToken is the GitLab access token forwarded by oauth2-proxy via
 * X-Forwarded-Access-Token. Authorization Bearer is only accepted as a local/test fallback.
 */
data class AuthSessionDetails(
    val accessToken: String,
    val user: String?,
    val email: String?,
    val groups: List<String>,
)
