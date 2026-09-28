<img align="right" width="250" height="47" src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" /> <br />

# Template Editor Backend

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#release-notes">Release Notes</a></li>
        <li><a href="#contributions-and-acknowledgements">Contributions and Acknowledgements</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#build">Build</a></li>
        <li><a href="#authentication">Authentication</a></li>
        <li><a href="#relevant-configuration">Relevant Configuration</a></li>
        <li><a href="#public-endpoints">Public Endpoints</a></li>
        <li><a href="#available-endpoints">Available Endpoints</a></li>
        <li><a href="#safeguards">Memory & Performance Safeguards</a></li>
      </ul>
    </li>
    <li>
      <a href="#usage">Usage</a>
       <ul>
        <li><a href="#openapi">OpenAPI Documentation</a></li>
        <li><a href="#mermaid-workflow">Mermaid Workflow Documentation</a></li>
      </ul>
	</li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#additional-notes">Additional Notes and Disclaimer from gematik GmbH</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project
Backend service for the Template Editor.

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

### Contributions and Acknowledgements

This open source project was developed in cooperation with the German Federal Institute for Drugs and Medical Devices (BfArM) on the basis of Section 355 (12-14) of the German Social Code Book V (SGB V).
As part of the projects implementation, the fbeta GmbH and Fraunhofer FOKUS were commissioned to provide software development services.

We would like to thank all parties involved for their constructive and trusted collaboration.

## Getting Started

### Build

You can build the project from the command line using Gradle.

**Note:** Make sure to have Gradle installed and configured in your PATH.

```bash
gradle build
```

#### Benchmarks

Benchmarks are implemented using JMH (Java Microbenchmark Harness). To run the benchmarks, follow the instructions from: [Benchmark Setup](./src/test/kotlin/de/gematik/zts/templateeditor/jmh/README.md)

### Authentication

`oauth2-proxy` handles user authentication before traffic reaches this service.

The backend does not start an OAuth flow, does not exchange authorization codes,
and does not issue or manage its own tokens.

Every protected request must contain the GitLab access token forwarded by
`oauth2-proxy`:

```http
X-Forwarded-Access-Token: <gitlab-access-token>
```

The same token is forwarded unchanged to GitLab API calls.

The backend must not be exposed directly. It is expected to be reachable only
through `oauth2-proxy`. The proxy must strip or overwrite incoming
`X-Forwarded-*` headers from clients.

`oauth2-proxy` should also forward user headers:

```http
X-Forwarded-User: <username>
X-Forwarded-Email: <email>
X-Forwarded-Groups: reviewers,dev/publishers
```

`X-Forwarded-Groups` is used for reviewer checks. If the groups header is not
present, the service falls back to GitLab `/oauth/userinfo` with the Bearer token.

### Relevant Configuration

| Variable                  | Default                                 | Description                                         |
|---------------------------|-----------------------------------------|-----------------------------------------------------|
| `GITLAB_BASE_URL`         | `https://gitlab.terminologien.bfarm.de` | GitLab base URL                                     |
| `GITLAB_USERINFO_PATH`    | `/oauth/userinfo`                       | Userinfo path used as fallback for groups/user data |
| `GITLAB_GROUP_PATH_ID`    | `17954`                                 | GitLab group/project path used by the adapters      |
| `GITLAB_REVIEWERS_GROUP`  | `Reviewers`                             | Reviewer group name/path suffix                     |
| `CORS_ALLOWED_ORIGINS`    | `http://localhost`                      | Allowed browser origin                              |
| `SERVER_PORT`             | `8080`                                  | Application HTTP port                               |
| `MANAGEMENT_PORT`         | `8081`                                  | Actuator port                                       |
| `HTTP_CONNECTION_TIMEOUT` | `120`                                   | GitLab HTTP timeout in seconds                      |

### Public Endpoints

Only infrastructure and documentation endpoints are public:

- `/actuator/**`
- `/ping`
- `/v3/api-docs`
- `/v3/api-docs.yaml`
- `/swagger-ui.html`
- `/swagger-ui/**`

All application endpoints require a forwarded GitLab access token.

#### User Roles

The system implements a two-tier role model:

- **Regular users**: can create branches, commit, create merge requests, manage versions.
- **Reviewers**:
    - the name of the GitLab group is configured with the environment variable `GITLAB_REVIEWERS_GROUP`
    - can only commit to a branch if an open merge request exists
    - can approve and merge MRs
    - **cannot** delete versions.

### Available Endpoints

#### CORS Handling

CORS is configured to allow requests only from `CORS_ALLOWED_ORIGINS`, with methods limited
to `GET`, `POST`, `DELETE`. This means `PUT` and `PATCH` are not allowed from the browser.

#### Authentication

| Method | Path             | Description                                                                             |
|--------|------------------|-----------------------------------------------------------------------------------------|
| `GET`  | `/auth/userinfo` | Information about the currently authenticated user, including backend reviewer mapping. |

#### Projects

| Method | Path        | Description                                                  |
|--------|-------------|--------------------------------------------------------------|
| `GET`  | `/projects` | Returns a paginated, searchable list of terminology projects |

#### Versions

| Method   | Path                                       | Description                                                |
|----------|--------------------------------------------|------------------------------------------------------------|
| `GET`    | `/projects/{projectId}/versions`           | Lists available terminology versions for a project         |
| `DELETE` | `/projects/{projectId}/versions/{version}` | Deletes a terminology version (not available to reviewers) |

#### Workspaces

| Method | Path                     | Description                                                               |
|--------|--------------------------|---------------------------------------------------------------------------|
| `GET`  | `/workspaces`            | Lists branches (workspaces) for a repository                              |
| `GET`  | `/workspaces/details`    | Returns full workspace details for a branch and version                   |
| `POST` | `/workspaces/commit`     | Commits a batch of file changes; optionally creates a merge request       |
| `POST` | `/workspaces/commitFile` | Uploads, validates and commits a single file (ZIP / FHIR JSON / FHIR XML) |
| `POST` | `/workspaces/review`     | Opens a merge request for a branch (without commit)                       |
| `GET`  | `/workspaces/openByMR`   | Opens a workspace using a merge request IID                               |

The `POST /workspaces/commitFile` endpoint validates the uploaded file, based on its content type:

- `.zip` files are validated by checking the ZIP magic bytes
- `.json` files are validated as FHIR JSON (root `resourceType` must be `ValueSet`, `ConceptMap`, or `CodeSystem`)
- `.xml` files are validated as FHIR XML (root element must be `ValueSet`, `ConceptMap`, or `CodeSystem`)
- Other file types are rejected.

##### File Upload Streaming (Temp-File Approach)

To avoid excessive heap memory usage during large file uploads, the `commitFile`
endpoint uses a temp-file streaming strategy instead of buffering the entire file in memory:

1. Stream to disk: the multipart upload is streamed directly to a temporary file in the
   configured `UPLOAD_TEMP_DIR` (default: `/tmp`) via `FilePart.transferTo()`. This keeps heap
   usage near zero during the upload phase.
2. Stream-based validation: the file is validated using `InputStream`-based methods — only
   the minimum data needed is read (e.g., 4 bytes for ZIP magic number checks). The raw file
   bytes are never fully loaded into heap memory.
3. Streaming Base64 encoding: the file is read from disk through a streaming Base64 encoder
   (`Base64.getEncoder().wrap()`), producing the encoded string via a scoped
   `ByteArrayOutputStream`. The intermediate buffer is eligible for GC immediately after the
   string is produced, keeping peak memory to roughly 1× the Base64 output size.
4. Direct GitLab commit: the Base64 content is sent directly to the GitLab commit API
   as a `GitLabCommitAction`, bypassing the intermediate `CommitRequest` / `CommitChange`
   wrapper chain to avoid unnecessary object copies.
5. Cleanup: the temporary file is always deleted after the commit completes, even
   on error or cancellation (`Mono.usingWhen`).

As the only requirement, the `UPLOAD_TEMP_DIR` must point to a writable volume, typically larger more than twice than
the defined `VALIDATION_COMMIT_CONTENT_MAX_SIZE`.

#### Workspace Comments

| Method | Path                         | Description                                                   |
|--------|------------------------------|---------------------------------------------------------------|
| `GET`  | `/workspaces/comments`       | Lists inline review comments for a merge request              |
| `POST` | `/workspaces/comments`       | Creates an inline review comment                              |
| `POST` | `/workspaces/comments/reply` | Replies to an existing comment thread; optionally resolves it |

#### Reviews

| Method | Path               | Description                                                       |
|--------|--------------------|-------------------------------------------------------------------|
| `GET`  | `/reviews`         | Lists merge requests across the group (filterable by state)       |
| `POST` | `/reviews/approve` | Approves and auto-merges a merge request (reviewer role required) |

#### Management / Monitoring

> Runs separately on **port 8081** with base path **`/actuator`**. All management endpoints are
> **read-only**.

| Method | Path                   | Description                                            |
|--------|------------------------|--------------------------------------------------------|
| `GET`  | `/actuator`            | Overview of available management endpoints             |
| `GET`  | `/actuator/health`     | Health status (liveness + readiness probes)            |
| `GET`  | `/actuator/metrics`    | Lists metrics                                          |
| `GET`  | `/actuator/prometheus` | Prometheus metrics export                              |
| `GET`  | `/actuator/info`       | Info endpoint (includes `management.info.env.enabled`) |
| `GET`  | `/actuator/sbom`       | Software Bill of Materials (when available)            |

#### Developer Tools

| Method | Path                | Description                  |
|--------|---------------------|------------------------------|
| `GET`  | `/v3/api-docs`      | OpenAPI specification (JSON) |
| `GET`  | `/v3/api-docs.yaml` | OpenAPI specification (YAML) |
| `GET`  | `/swagger-ui.html`  | Swagger UI                   |

---

### Memory & Performance Safeguards

The service applies several safeguards to minimize heap memory usage,
especially during file uploads and workspace-detail fetching.

#### Request Body Size Limits

A `PayloadSizeLimitFilter` (WebFilter) rejects non-multipart `POST`/`PUT` requests whose
`Content-Length` exceeds 10 MB, the same limit as `spring.http.codecs.max-in-memory-size`.
This provides early rejection *before* the body is deserialized, preventing oversized JSON
payloads from consuming heap memory. Multipart uploads are excluded because they are streamed
to disk.

#### File Validation (InputStream-based)

`FileTypeValidator` provides `InputStream`-based overloads for all validation methods
(`isFileTypeZip`, `isValidFhirJson`, `isValidFhirXml`). These avoid loading the full file
`byte[]` into heap:

- ZIP validation only reads the first 4 bytes (magic number check).
- FHIR JSON/XML validation streams the content through Jackson / DOM parsers directly.

A new `DocumentBuilder` is created per XML parse call to avoid thread-safety issues
with the shared (non-thread-safe) `DocumentBuilder` class.

#### GitLab File Fetching

`GitLabClient.fetchFileText()` uses a `StringBuilder`-based `Flux.collect()` instead of
`collectList() + joinToString()`. This avoids creating an intermediate `List<String>` and
a second full copy of the file content.

#### Workspace Detail Fetching

All unbounded `flatMap` calls in `DetailFetcher` (template JSONs, markdown files, input
file metadata) are limited to a concurrency of 4. This bounds the number of files held
in memory simultaneously, preventing heap exhaustion when workspaces contain many templates.

#### WebClient Caching

`GitLabClient` caches the base `WebClient` instance (built from the injected
`WebClient.Builder`) and uses `mutate()` for per-request authorization headers. This avoids
rebuilding the `WebClient` on every API call and reduces GC pressure under high concurrency.

---

## Usage

### OpenAPI Documentation

An up-to-date OpenAPI specification can be found at:

```
dokumentation/openapi.yaml
```

It describes all existing endpoints including request/response structures.

### Mermaid Workflow Documentation

The current workflow specification can be found at:

```
dokumentation/mermaid/*.mmd
```

---

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

Copyright 2026 gematik GmbH

Apache License, Version 2.0

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Parts of this software and - in isolated cases - content such as text or images may have been developed using the support of AI tools. They are subject to the same reviews, tests, and security checks as any other contribution. The functionality of the software itself is not based on AI decisions.

## Contact
We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes.
This software is currently being tested to ensure its technical quality and legal compliance. Your feedback is highly valued.
If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: OSPO@gematk.de.
