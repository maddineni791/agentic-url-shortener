# Agentic URL Shortener SDLC Platform

This repository contains a runnable Java 21 Spring Boot platform for the Agentic-Proficient
Software Engineer assessment. The platform exposes REST APIs that accept a software
requirement, run deterministic or OpenAI-backed agents, generate implementation and test
file-operation proposals, apply those proposals inside isolated workspaces, run real Maven
validation, repair a deliberate failure scenario, expose review evidence, and require
exact-hash human approval before release completion.

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
$workflow
```

Inspect evidence:

```powershell
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/tasks" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/validation-attempts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/policies" -Credential $operator
```

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
$env:OPENAI_MODEL = "gpt-5.6-luna"
```

The deterministic provider is the default and requires no API key. Both providers use the
same structured model contracts, patch policy, repository mutation, validation, repair,
evidence, and approval pipeline.

## Limitations

This assessment implementation demonstrates the lifecycle inside a runnable platform.
Production identity-provider provisioning, certificate issuance, external secret
management, DNS, global ingress limits, egress policy, and actual multi-region deployment
remain operator responsibilities documented in `docs/PRODUCTION-DEPLOYMENT.md`.
