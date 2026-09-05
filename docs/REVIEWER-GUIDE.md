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

## Checkpoint 5

Expected behavior:

- Specialized agents produce typed outputs through the shared model abstraction.
- The ambiguity agent blocks materially underspecified requirements without relying on a
  scenario enum.
- The planner emits executor tasks with dependencies, gates, retry policy, and parallel
  work groups.
- Implementation, testing, and repair agents emit structured file-operation proposals.
- Security/risk and release-readiness agents emit hash-linked governance artifacts.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Representative tests:

- `SpecializedAgentTests.requirementAgentProducesValidatedRequirementArtifact`
- `SpecializedAgentTests.ambiguityAgentBlocksMateriallyUnderspecifiedRequirement`
- `SpecializedAgentTests.plannerProducesDynamicExecutionPlaneTasksWithDependenciesAndParallelBranches`
- `SpecializedAgentTests.implementationAndTestAgentsProduceStructuredFileOperationProposals`

## Checkpoint 6

Expected behavior:

- `POST /api/workflows` invokes the deterministic agent runner immediately.
- Clear URL-shortener requirements produce durable task records, generated artifacts, and
  audit events, ending in `AWAITING_RELEASE_APPROVAL`.
- Ambiguous requirements produce requirement and ambiguity artifacts, then pause in
  `AWAITING_CLARIFICATION` before implementation or test proposal generation.
- Evidence is available through `tasks`, `artifacts`, artifact content, and audit APIs.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 6: 24 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 7

Expected behavior:

- The implementation and test proposals are not ignored; they are read back from persisted
  artifacts and applied in an isolated workspace.
- Patch policy rejects unsafe paths, unsupported extensions, duplicate operations, content
  over limits, and update/delete operations without expected hashes.
- Successful workflows expose `patch-policy.json`, `applied-file-operations.json`,
  `unified-diff.patch`, and `source-manifest.json`.
- Audit events include `patch.applied` for successful policy-controlled mutation.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 7: 26 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 8

Expected behavior:

- Successful generated workspaces run a real fixed Maven Wrapper validation command.
- Validation attempts persist exit code, duration, timeout flag, failure classification,
  and bounded stdout/stderr.
- `GET /api/workflows/{workflowId}/validation-attempts` exposes validation evidence.
- The deterministic `repair-demonstration` scenario first fails compilation, invokes the
  repair agent with actual validation evidence, applies a repaired proposal, and validates
  successfully on the second attempt.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 8: 27 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 9

Expected behavior:

- `POST /api/urls` creates a persisted short URL.
- `GET /r/{code}` returns `302 Found` with `Location` and records redirect analytics.
- `GET /api/urls/{code}` returns inspection data including active state, expiry, and
  redirect count.
- `PATCH /api/urls/{code}/deactivate` disables future redirects.
- Expired, unknown, malformed, unsupported-scheme, and user-info URLs return RFC 9457
  Problem Details with stable error codes.
- Flyway applies `V2__url_shortener_core.sql`.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 9: 32 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 10

Expected behavior:

- URL creation is rate-limited and returns HTTP 429 with `Retry-After`.
- Short codes include the configured regional prefix.
- Redirect analytics include total redirects and UTC daily counts.
- Localhost, private IPs, configured blocked hosts, unsupported schemes, and user-info are
  rejected.
- Concurrent URL creation produces unique codes.
- Cleanup can remove retained redirect events and inactive/expired URLs.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 10: 35 tests, 0 failures, 0 errors, 0 skipped.

## Checkpoint 11

Expected behavior:

- `POST /api/workflows` accepts an `Idempotency-Key` header.
- Replaying the same authenticated request and idempotency key returns the original
  workflow instead of creating duplicate workflow state.
- Reusing the same key with different request content returns HTTP 409 Problem Details
  with code `CONFLICT`.
- Workflow tasks have database-backed lease owner, lease expiry, and fencing-token state.
- A task can be claimed only when unleased or expired.
- Heartbeat and completion require the current lease owner and fencing token, so stale
  workers cannot complete a task after losing ownership.
- `GET /api/workflows/{workflowId}/tasks` exposes lease and fencing fields for reviewer
  evidence.

Example idempotent submission:

```powershell
$body = @{
  scenarioKey = "greenfield-url-shortener"
  requirement = "Build a URL shortener with redirect analytics."
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri http://localhost:8080/api/workflows `
  -Method Post `
  -Credential (New-Object pscredential "operator",(ConvertTo-SecureString "operator-pass" -AsPlainText -Force)) `
  -Headers @{ "Idempotency-Key" = "reviewer-submit-001" } `
  -ContentType "application/json" `
  -Body $body
```

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 11: 38 tests, 0 failures, 0 errors, 0 skipped.
