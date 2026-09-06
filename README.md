# Agentic URL Shortener SDLC Platform

This repository contains a runnable Java 21 Spring Boot platform for the Agentic-Proficient
Software Engineer assessment. The platform exposes REST APIs that accept a software
requirement, run deterministic or OpenAI-backed agents, generate multi-file implementation
and test file-operation proposals, apply those proposals inside isolated workspaces, run
real Maven validation, repair a deliberate failure scenario, expose review evidence, and
require exact-hash human approval before release completion.

The concrete product slice is a URL shortener with PostgreSQL persistence, Flyway
migrations, RFC 9457 errors, OpenAPI, health probes, Prometheus metrics, rate limiting,
blocked-host/private-address validation, regional short codes, redirect analytics, and
retention cleanup.

## Requirements

- Java 21
- Docker Desktop
- PowerShell on Windows

## Validate Locally

```powershell
.\mvnw.cmd clean verify
```

Tests use H2 in PostgreSQL compatibility mode. Verification runs application, agent/model,
repository sandbox, workflow API, URL-shortener, Flyway, Prometheus-rule, and JaCoCo checks.

## Run With Docker Desktop PostgreSQL

```powershell
docker rm -f agentic-postgres 2>$null

docker run --name agentic-postgres `
  -e POSTGRES_DB=agentic `
  -e POSTGRES_USER=agentic `
  -e POSTGRES_PASSWORD=agentic `
  -p 5432:5432 `
  -d postgres:16-alpine
```

```powershell
$env:AGENTIC_DB_URL = "jdbc:postgresql://localhost:5432/agentic"
$env:AGENTIC_DB_USERNAME = "agentic"
$env:AGENTIC_DB_PASSWORD = "agentic"
$env:AGENTIC_MODEL_PROVIDER = "deterministic"
$env:AGENTIC_URL_REGION_PREFIX = "us"
$env:AGENTIC_BLOCKED_HOSTS = "blocked.example"

.\mvnw.cmd spring-boot:run
```

Useful URLs:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/health/readiness`
- `http://localhost:8080/actuator/prometheus`
- `http://localhost:8080/v3/api-docs`
- `http://localhost:8080/swagger-ui.html`

## Run With Docker Compose

```powershell
.\mvnw.cmd clean package
docker compose up --build
```

Services:

- orchestrator A: `http://localhost:8080`
- orchestrator B: `http://localhost:8081`
- Prometheus: `http://localhost:9090`
- PostgreSQL: `localhost:5432`

## Local Credentials

| Username | Password | Role |
| -------- | -------- | ---- |
| `operator` | `operator-pass` | `OPERATOR` |
| `change-approver` | `change-pass` | `CHANGE_APPROVER` |
| `release-approver` | `release-pass` | `RELEASE_APPROVER` |

## URL Shortener Quick Check

```powershell
$created = Invoke-RestMethod `
  -Uri http://localhost:8080/api/urls `
  -Method Post `
  -ContentType "application/json" `
  -Body (@{ url = "https://example.com/docs"; expiresAt = $null } | ConvertTo-Json)

$code = $created.shortCode
$created

Invoke-WebRequest "http://localhost:8080/r/$code" -MaximumRedirection 0 -SkipHttpErrorCheck
Invoke-RestMethod "http://localhost:8080/api/urls/$code"
Invoke-RestMethod "http://localhost:8080/api/urls/$code/analytics"
```

## Agentic Workflow Quick Check

```powershell
$operator = New-Object pscredential "operator",(ConvertTo-SecureString "operator-pass" -AsPlainText -Force)

$body = @{
  scenarioKey = "brownfield-analytics"
  requirement = "Add URL creation API and redirect endpoint with PostgreSQL storage, rate limiting, blocked host validation, expiry, retention cleanup, and UTC daily analytics."
} | ConvertTo-Json

$workflow = Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential $operator `
  -Headers @{ "Idempotency-Key" = "reviewer-workflow-001" } `
  -ContentType "application/json" `
  -Body $body

$workflowId = $workflow.id
$workflow   # status: AWAITING_CHANGE_APPROVAL
```

Approve the change gate with the exact plan hash to run the build segment:

```powershell
$changeApprover = New-Object pscredential "change-approver",(ConvertTo-SecureString "change-pass" -AsPlainText -Force)
$plan = Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/engineering-plan.json" -Credential $changeApprover
Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/$workflowId/approvals/change" -Method Post -Credential $changeApprover `
  -ContentType "application/json" -Body (@{ artifactHash = $plan.sha256; reason = "Reviewed plan." } | ConvertTo-Json)
```

Inspect evidence (now `AWAITING_RELEASE_APPROVAL`):

```powershell
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/tasks" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/validation-attempts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/policies" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/audit-events" -Credential $operator   # includes workflow.build-graph-executed
```

The deterministic implementation agent proposes a generated URL-shortener slice instead of
a status-only marker. The proposal includes:

- `src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSlice.java`
- `src/main/java/com/assessment/generated/urlshortener/GeneratedShortUrl.java`
- `src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerController.java`
- `src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerRequest.java`
- `src/test/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSliceTests.java`

Approve release with exact current evidence:

```powershell
$outcome = Invoke-RestMethod `
  "http://localhost:8080/api/workflows/$workflowId/artifacts/engineering-outcome.json" `
  -Credential $operator

$releaseApprover = New-Object pscredential "release-approver",(ConvertTo-SecureString "release-pass" -AsPlainText -Force)

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/workflows/$workflowId/approvals/release" `
  -Method Post `
  -Credential $releaseApprover `
  -ContentType "application/json" `
  -Body (@{ artifactHash = $outcome.sha256; reason = "Reviewed exact engineering outcome evidence." } | ConvertTo-Json)
```

## Generated Workflow Files

Workflow execution writes generated files only inside an isolated workspace. Find the path:

```powershell
$applied = Invoke-RestMethod `
  "http://localhost:8080/api/workflows/$workflowId/artifacts/applied-file-operations.json" `
  -Credential $operator

($applied.content | ConvertFrom-Json).workspacePath
```

Key evidence artifacts:

- `implementation-proposal.json`
- `test-proposal.json`
- `unified-diff.patch`
- `source-manifest.json`
- `validation-attempt-1.json`
- `repair-proposal.json` for the repair scenario
- `engineering-plan.json`
- `engineering-outcome.json`

## Optional OpenAI Provider

```powershell
$env:AGENTIC_MODEL_PROVIDER = "openai"
$env:OPENAI_API_KEY = "<api-key>"
$env:OPENAI_BASE_URL = "https://api.openai.com"
$env:OPENAI_MODEL = "gpt-4o-mini"
```

The deterministic provider is the default and requires no API key. Both providers use the
same structured model contracts, patch policy, repository mutation, validation, repair,
evidence, and approval pipeline.

## Orchestration Controls

- **Change gate** — every revision pauses in `AWAITING_CHANGE_APPROVAL` after
  `engineering-plan.json`; no workspace is created until a change approver supplies the
  exact plan hash. The build segment then runs as a task graph (`TaskGraphExecutor`) with
  `implement-change` ∥ `generate-tests` ∥ `security-risk-review` and a barrier at patch
  application.
- **Clarification** — `POST /api/workflows/{id}/clarifications` opens revision N+1 with the
  original requirement plus answer lineage and resumes execution.
- **Safe stop / rollback** — `POST /api/workflows/{id}/safe-stop` cancels a non-terminal
  workflow and restores its workspace from a manifest-verified baseline snapshot; rollback
  also runs automatically when the bounded repair budget is exhausted.
- **Restart recovery** — `WorkflowRecoveryService` resumes any `RUNNING` revision from its
  durable PostgreSQL checkpoint on startup and every `agentic.orchestration.recovery-interval`.
- **Async submission** — set `AGENTIC_ORCHESTRATION_ASYNC=true` to return from `POST
  /api/workflows` immediately and run orchestration on the bounded executor.

See `docs/TRACEABILITY.md` for requirement-by-requirement status.

## Limitations

This assessment implementation demonstrates the lifecycle inside a runnable platform.
The deterministic provider is template-based (requirement understanding, ambiguity, and
repair are keyword/template driven); open-ended requirements need the OpenAI provider.
Generated code is validated in isolation and not merged into the platform's own shortener
module. Per-node task leases are persisted and tested but not yet claimed by the in-process
runner. Production identity-provider provisioning, certificate issuance, external secret
management, DNS, global ingress limits, egress policy, and actual multi-region deployment
remain operator responsibilities documented in `docs/PRODUCTION-DEPLOYMENT.md`.
