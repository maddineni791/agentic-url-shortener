# Engineering Outcome

The implemented platform demonstrates the assessment lifecycle:

requirement -> requirement analysis -> general ambiguity decision -> clarification and a
new lineage-preserving revision when needed -> repository-aware plan -> **exact-evidence
change approval** -> dependency-graph build (implementation, tests and risk review in
parallel; patch as the barrier) -> real Maven validation -> bounded repair or verified
rollback -> documentation and risk review -> **exact-evidence release approval** ->
durable outcome. Interrupted revisions are resumed automatically on restart.

## Delivered Artifacts

- Runnable Spring Boot REST API; functional URL shortener on PostgreSQL/Flyway.
- Provider-neutral model gateway; deterministic provider (no API key) and OpenAI Responses
  provider with identical contracts.
- Two-gate governance: change approval binds the exact `engineering-plan.json` hash before
  any workspace is created; release approval binds the exact `engineering-outcome.json`
  hash. A new revision invalidates the superseded revision.
- Clarification lifecycle: `POST /clarifications` opens revision N+1 with the original
  requirement plus answer lineage and resumes execution.
- `TaskGraphExecutor` runs the declared build graph wave by wave with a real parallel
  branch (`implement-change` ∥ `generate-tests` ∥ `security-risk-review`) and a
  synchronization barrier at patch application.
- Isolated workspace patch application with policy checks, SHA-256 manifests, an immutable
  baseline snapshot, and manifest-verified rollback.
- Real Maven `clean test` validation (executable sandbox wrapper, shared warm local repo,
  configurable timeout) with bounded logs and failure classification; bounded repair.
- Safe stop: cancel + verified rollback + `SAFE_STOPPED`.
- Durable state in PostgreSQL with per-step commits; `WorkflowRecoveryService` resumes any
  `RUNNING` revision on startup and on a schedule.
- Optional asynchronous submission (`agentic.orchestration.async`).
- Idempotency keys; fenced task-lease persistence; Prometheus metrics + recording rules.
- Dockerfile, Docker Compose (two instances), GitHub Actions CI.

## Validation Evidence

```powershell
.\mvnw.cmd clean verify
```

Expected result: BUILD SUCCESS with 55 tests passing, Flyway migration validation, JAR
packaging, JaCoCo report and threshold check. `git diff --check` is clean and
`docker compose config` validates.

## Assumptions, Risks And Limits

- The deterministic provider is template-based so the assessment runs without credentials.
  Requirement understanding, ambiguity classification, and repair are keyword/template
  driven under this provider; open-ended requirements need the OpenAI provider.
- Generated code is validated in `com.assessment.generated.urlshortener` in isolation; it
  is not merged into the platform's own shortener module.
- Per-node task leases are persisted and tested but not yet claimed by the in-process
  runner; multi-instance operation relies on `async` submission plus restart recovery.
- Production OIDC/JWT provisioning, TLS issuance, external secrets, DNS, WAF, global ingress
  rate limits, and actual multi-region deployment are operator responsibilities
  (`PRODUCTION-DEPLOYMENT.md`).

Full requirement-by-requirement status is in `docs/TRACEABILITY.md`.
