# Reviewer Guide

This guide demonstrates the implemented platform behavior with PowerShell commands.

## Validate

```powershell
.\mvnw.cmd clean verify
```

Expected result: build success, all tests passing, Flyway migrations validated, and JaCoCo
checks passing.

## Start The App

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

Health and discovery:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-WebRequest http://localhost:8080/actuator/prometheus
Invoke-RestMethod http://localhost:8080/v3/api-docs
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Credentials

| Username | Password | Role |
| -------- | -------- | ---- |
| `operator` | `operator-pass` | `OPERATOR` |
| `change-approver` | `change-pass` | `CHANGE_APPROVER` |
| `release-approver` | `release-pass` | `RELEASE_APPROVER` |

## URL Shortener

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

Deactivate:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/urls/$code/deactivate" `
  -Method Patch
```

Validation failures:

```powershell
Invoke-WebRequest `
  -Uri http://localhost:8080/api/urls `
  -Method Post `
  -ContentType "application/json" `
  -Body (@{ url = "ftp://example.com/file" } | ConvertTo-Json) `
  -SkipHttpErrorCheck

Invoke-WebRequest `
  -Uri http://localhost:8080/api/urls `
  -Method Post `
  -ContentType "application/json" `
  -Body (@{ url = "https://localhost/admin" } | ConvertTo-Json) `
  -SkipHttpErrorCheck

Invoke-WebRequest `
  -Uri http://localhost:8080/api/urls `
  -Method Post `
  -ContentType "application/json" `
  -Body (@{ url = "https://blocked.example/page" } | ConvertTo-Json) `
  -SkipHttpErrorCheck
```

## Agentic Workflow

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

Expected status: `AWAITING_CHANGE_APPROVAL`. The design segment has published
`engineering-plan.json`; no isolated workspace or patch exists yet.

## Exact Change Approval

```powershell
$changeApprover = New-Object pscredential "change-approver",(ConvertTo-SecureString "change-pass" -AsPlainText -Force)

$plan = Invoke-RestMethod `
  "http://localhost:8080/api/workflows/$workflowId/artifacts/engineering-plan.json" `
  -Credential $changeApprover

# Wrong hash is rejected and persisted as a REJECTED approval.
Invoke-WebRequest `
  -Uri "http://localhost:8080/api/workflows/$workflowId/approvals/change" `
  -Method Post `
  -Credential $changeApprover `
  -ContentType "application/json" `
  -Body (@{ artifactHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; reason = "guessed" } | ConvertTo-Json) `
  -SkipHttpErrorCheck

# Exact current-revision plan hash resumes the build segment.
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/workflows/$workflowId/approvals/change" `
  -Method Post `
  -Credential $changeApprover `
  -ContentType "application/json" `
  -Body (@{ artifactHash = $plan.sha256; reason = "Reviewed engineering plan." } | ConvertTo-Json)

Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId" -Credential $operator
```

Expected status after change approval: `AWAITING_RELEASE_APPROVAL`.

Inspect execution evidence:

```powershell
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/tasks" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/validation-attempts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/audit-events" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/policies" -Credential $operator
```

Inspect generated artifacts:

```powershell
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/normalized-requirement.json" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/engineering-plan.json" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/implementation-proposal.json" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/test-proposal.json" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/unified-diff.patch" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/source-manifest.json" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/artifacts/engineering-outcome.json" -Credential $operator
```

The implementation proposal should contain generated production files under
`src/main/java/com/assessment/generated/urlshortener/` and the test proposal should contain
`GeneratedUrlShortenerSliceTests`. These generated files are the patch applied to the
isolated workspace; they are not manually supplied node-completion text.

## Idempotency

Run the same workflow submission again with the same `Idempotency-Key`. The response
returns the original workflow ID. Change the requirement while keeping the same key to
receive HTTP 409 Problem Details with code `CONFLICT`.

## Exact Release Approval

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

Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$workflowId/approvals" -Credential $operator
```

Expected status after approval: `COMPLETED`.

Invented hash rejection:

```powershell
Invoke-WebRequest `
  -Uri "http://localhost:8080/api/workflows/$workflowId/approvals/release" `
  -Method Post `
  -Credential $releaseApprover `
  -ContentType "application/json" `
  -Body (@{ artifactHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; reason = "wrong hash" } | ConvertTo-Json) `
  -SkipHttpErrorCheck
```

## Repair Demonstration

```powershell
$repairBody = @{
  scenarioKey = "repair-demonstration"
  requirement = "repair scenario: Add URL creation API and redirect endpoint with PostgreSQL storage, rate limiting, blocked host validation, expiry, retention cleanup, and UTC daily analytics."
} | ConvertTo-Json

$repair = Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential $operator `
  -ContentType "application/json" `
  -Body $repairBody

# Approve the change gate to run the build/validation/repair segment.
$repairPlan = Invoke-RestMethod "http://localhost:8080/api/workflows/$($repair.id)/artifacts/engineering-plan.json" -Credential $changeApprover
Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/$($repair.id)/approvals/change" -Method Post -Credential $changeApprover `
  -ContentType "application/json" -Body (@{ artifactHash = $repairPlan.sha256; reason = "reviewed" } | ConvertTo-Json)

Invoke-RestMethod "http://localhost:8080/api/workflows/$($repair.id)/validation-attempts" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($repair.id)/artifacts/repair-proposal.json" -Credential $operator
```

Expected evidence: two validation attempts, with the first failing (non-zero exit) and the
second passing after repair.

## Ambiguity Demonstration

```powershell
$ambiguousBody = @{
  scenarioKey = "ambiguous-requirement"
  requirement = "Make links better."
} | ConvertTo-Json

$ambiguous = Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential $operator `
  -ContentType "application/json" `
  -Body $ambiguousBody

Invoke-RestMethod "http://localhost:8080/api/workflows/$($ambiguous.id)" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($ambiguous.id)/artifacts/ambiguity-decision.json" -Credential $operator
```

Expected status: `AWAITING_CLARIFICATION`. No patch artifacts are created for this
revision. Inspect `normalized-requirement.json` as well to see the missing requirement
dimensions that drive the ambiguity decision.

## Clarification And Revision 2

```powershell
$clarifyBody = @{
  questionId = "acceptance-and-scope"
  answer = "Provide REST endpoints to create short URLs and redirect, persist records in PostgreSQL, require API key authentication and rate limiting, apply 30 day expiry, and expose UTC daily analytics."
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/api/workflows/$($ambiguous.id)/clarifications" `
  -Method Post `
  -Credential $operator `
  -ContentType "application/json" `
  -Body $clarifyBody

Invoke-RestMethod "http://localhost:8080/api/workflows/$($ambiguous.id)" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($ambiguous.id)/artifacts/clarified-requirement.txt" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($ambiguous.id)/audit-events" -Credential $operator
```

Expected: `currentRevision` becomes `2`, status becomes `AWAITING_CHANGE_APPROVAL`, the
audit trail contains `workflow.clarification-received` and `workflow.revision-created`, and
a second clarification attempt returns HTTP 409.

## Safe Stop And Rollback

```powershell
$safe = Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential $operator `
  -ContentType "application/json" `
  -Body $body   # any concrete scenario

# Approve the change gate so an isolated workspace exists, then stop and roll back.
$safePlan = Invoke-RestMethod "http://localhost:8080/api/workflows/$($safe.id)/artifacts/engineering-plan.json" -Credential $changeApprover
Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/$($safe.id)/approvals/change" -Method Post -Credential $changeApprover `
  -ContentType "application/json" -Body (@{ artifactHash = $safePlan.sha256; reason = "reviewed" } | ConvertTo-Json)

Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/$($safe.id)/safe-stop" -Method Post -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($safe.id)" -Credential $operator
Invoke-RestMethod "http://localhost:8080/api/workflows/$($safe.id)/artifacts/safe-stop-evidence.json" -Credential $operator
```

Expected: status `SAFE_STOPPED`; `safe-stop-evidence.json` shows `rollbackVerified: true`
and `workspaceExisted: true`; a second safe-stop returns HTTP 409.

## Restart Recovery

`WorkflowRecoveryService` resumes any workflow left `RUNNING` by a stopped instance, on
startup and every `agentic.orchestration.recovery-interval` (default 30s). To observe it,
submit a workflow, `docker kill` / stop the app while the build segment runs, then restart
it: the `workflow.recovery-resumed` audit event records how the revision was re-driven from
its last durable checkpoint. `WorkflowRecoveryServiceTests` exercises the checkpoint logic
without a real restart.

## Asynchronous Submission

Set `AGENTIC_ORCHESTRATION_ASYNC=true` before starting the app. `POST /api/workflows` then
returns immediately with status `SUBMITTED`/`RUNNING`; poll `GET /api/workflows/{id}` until
it reaches `AWAITING_CHANGE_APPROVAL`.

## Docker Compose

```powershell
.\mvnw.cmd clean package
docker compose config
docker compose up --build
```

Then check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-WebRequest http://localhost:8080/actuator/prometheus
Invoke-WebRequest http://localhost:9090
```

## Cleanup

```powershell
docker compose down -v
docker rm -f agentic-postgres 2>$null
Remove-Item Env:\AGENTIC_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:\AGENTIC_DB_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:\AGENTIC_DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:\AGENTIC_MODEL_PROVIDER -ErrorAction SilentlyContinue
Remove-Item Env:\OPENAI_API_KEY -ErrorAction SilentlyContinue
```
