# Reviewer Guide

This guide will be expanded at each checkpoint with exact commands, credentials, API calls,
and expected evidence.

## Checkpoint 1

Expected behavior:

- The repository is a Java 21 Spring Boot project.
- The application exposes `GET /api/platform`.
- The traceability matrix exists and identifies incomplete requirements honestly.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

## Checkpoint 2

Expected behavior:

- Flyway applies the durable workflow schema.
- The persistence adapter can create and read workflows, revisions, tasks, artifacts, and
  audit events.
- Audit payloads redact common secret assignments while retaining an original payload hash.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 2: 4 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 3

Expected behavior:

- `GET /api/scenarios` and OpenAPI endpoints are public.
- `POST /api/workflows` requires the `operator` Basic-auth user.
- Change approval requires `change-approver`.
- Release approval requires `release-approver`.
- Invalid request bodies return RFC 9457 Problem Details with `code` and `correlationId`.
- Workflow submission persists a workflow, revision 1, and audit event.

Local credentials:

| Username | Password | Role |
| -------- | -------- | ---- |
| `operator` | `operator-pass` | `OPERATOR` |
| `change-approver` | `change-pass` | `CHANGE_APPROVER` |
| `release-approver` | `release-pass` | `RELEASE_APPROVER` |

Example workflow submission:

```powershell
$body = @{
  scenarioKey = "greenfield-url-shortener"
  requirement = "Build a URL shortener with redirect analytics."
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential (New-Object pscredential "operator",(ConvertTo-SecureString "operator-pass" -AsPlainText -Force)) `
  -ContentType "application/json" `
  -Body $body
```

Expected test result for checkpoint 3: 10 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 4

Expected behavior:

- Application startup remains deterministic without `OPENAI_API_KEY`.
- `agentic.model.provider=deterministic` is the default.
- Deterministic and OpenAI providers share `ModelRequest` and `ModelResult`.
- Model prompts are redacted before invocation.
- Required structured fields are enforced after provider output.
- Model input/output character bounds are enforced.
- OpenAI Responses API calls use `POST /v1/responses`, bearer auth, configured model, and
  bounded HTTP timeouts.

Configuration:

```powershell
$env:AGENTIC_MODEL_PROVIDER = "deterministic"
$env:AGENTIC_MODEL_TIMEOUT = "PT20S"
$env:AGENTIC_MODEL_MAX_INPUT_CHARS = "20000"
$env:AGENTIC_MODEL_MAX_OUTPUT_CHARS = "20000"
```

Optional OpenAI mode:

```powershell
$env:AGENTIC_MODEL_PROVIDER = "openai"
$env:OPENAI_API_KEY = "<api-key>"
$env:OPENAI_BASE_URL = "https://api.openai.com"
$env:OPENAI_MODEL = "gpt-5.6-luna"
```

Expected test result for checkpoint 4: 16 tests, 0 failures, 0 errors, 0 skipped.
