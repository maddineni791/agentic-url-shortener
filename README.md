# Agentic-Proficient Software Engineer Platform

This repository contains a Spring Boot based agentic SDLC orchestration platform for the
Agentic-Proficient Software Engineer assessment. The platform will expose REST APIs that
drive a requirement-to-code-to-test lifecycle against isolated repository workspaces, using
a functional URL shortener as the concrete engineering target.

The implementation is being developed in reviewable checkpoints. The assignment PDF is the
authoritative requirements source; reviewer-approved projects are used only as quality
references and are not copied.

## Current Checkpoint

Checkpoint 9 includes the runnable agentic workflow path plus the functional
URL-shortener core: URL creation, redirect, inspection, deactivation, expiry, PostgreSQL
persistence, Flyway migrations, validation, and RFC 9457 Problem Details.

## Local Development

Java 21 is required.

```powershell
.\mvnw.cmd clean verify
```

The default runtime profile expects PostgreSQL at
`jdbc:postgresql://localhost:5432/agentic` with username/password `agentic`/`agentic`.
Tests use an H2 database in PostgreSQL compatibility mode.

On Unix-like shells:

```bash
./mvnw clean verify
```

## Documentation

- [Traceability](docs/TRACEABILITY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Reviewer Guide](docs/REVIEWER-GUIDE.md)

## Deterministic Local Roles

Checkpoint 3 includes local Basic authentication for platform APIs:

| Username | Password | Role |
| -------- | -------- | ---- |
| `operator` | `operator-pass` | `OPERATOR` |
| `change-approver` | `change-pass` | `CHANGE_APPROVER` |
| `release-approver` | `release-pass` | `RELEASE_APPROVER` |

OpenAPI is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## URL Shortener Quick Check

Start PostgreSQL with Docker Desktop:

```powershell
docker run --name agentic-postgres `
  -e POSTGRES_DB=agentic `
  -e POSTGRES_USER=agentic `
  -e POSTGRES_PASSWORD=agentic `
  -p 5432:5432 `
  -d postgres:16-alpine
```

Run the app:

```powershell
$env:AGENTIC_DB_URL = "jdbc:postgresql://localhost:5432/agentic"
$env:AGENTIC_DB_USERNAME = "agentic"
$env:AGENTIC_DB_PASSWORD = "agentic"
$env:AGENTIC_MODEL_PROVIDER = "deterministic"
.\mvnw.cmd spring-boot:run
```

Create and inspect a short URL:

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/urls `
  -ContentType "application/json" `
  -Body (@{ url = "https://example.com/docs"; expiresAt = $null } | ConvertTo-Json)

$created
Invoke-WebRequest -MaximumRedirection 0 -Uri "http://localhost:8080/r/$($created.shortCode)"
Invoke-RestMethod -Uri "http://localhost:8080/api/urls/$($created.shortCode)"
Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/urls/$($created.shortCode)/deactivate"
```

## Model Providers

The default model provider is deterministic and requires no API key:

```powershell
$env:AGENTIC_MODEL_PROVIDER = "deterministic"
```

Optional OpenAI Responses API mode is configured only through environment variables:

```powershell
$env:AGENTIC_MODEL_PROVIDER = "openai"
$env:OPENAI_API_KEY = "<api-key>"
$env:OPENAI_MODEL = "gpt-5.6-luna"
```

Both providers use the same `ModelRequest` and `ModelResult` contracts. The platform
redacts common secret assignments before model invocation and enforces bounded input and
output sizes.
