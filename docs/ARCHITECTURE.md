# Architecture

The platform separates probabilistic reasoning from deterministic effects. Agents produce
structured artifacts. The orchestrator owns workflow state. Repository tools apply
policy-approved file operations in isolated workspaces. Validators run fixed build
commands. Humans approve exact current evidence by SHA-256 hash.

## Components

- `api`: REST controllers, RFC 9457 errors, correlation IDs, pagination, workflow evidence
  APIs, approval APIs, and URL-shortener APIs.
- `agents`: requirement, ambiguity, repository-analysis, planning, architecture,
  implementation, test-generation, repair, documentation, security, and release-readiness
  agents.
- `model`: provider-neutral `ModelGateway`, deterministic provider, and optional OpenAI
  Responses API provider.
- `orchestration`: lifecycle runner that invokes agents, persists artifacts, applies
  patches, validates workspaces, performs bounded repair, and produces final evidence.
- `repository`: isolated workspace creation, file-operation policy, atomic writes, unified
  diffs, and SHA-256 manifests.
- `validation`: fixed Maven Wrapper validation (`clean test`) with a configurable timeout
  (`agentic.validation.timeout`, default 3m), bounded logs, credential stripping from the
  child environment, exit-code capture, and failure classification. The sandboxed build
  reuses the platform's already-populated local repository and its `mvnw` copy is made
  executable, so it runs offline and deterministically under CI.
- `persistence`: Spring JDBC stores backed by PostgreSQL and Flyway.
- `urlshortener`: functional URL shortener used as the concrete engineering target.
- `observability`: Micrometer metrics, Prometheus scrape endpoint, and recording rules.

## Workflow Lifecycle

A revision runs in two governed segments separated by the change-approval gate.

**Design segment** (`POST /api/workflows` → `startRevision`):

1. requirement interpretation (`normalized-requirement.json`);
2. ambiguity analysis (`ambiguity-decision.json`);
3. dependency-aware task planning (`task-plan.json`, canonical `engineering-plan.json`);
4. pause in `AWAITING_CHANGE_APPROVAL` — **no repository workspace is created yet.**

**Build segment** (`POST /api/workflows/{id}/approvals/change` with the exact
`engineering-plan.json` hash → `resumeAfterChangeApproval`), executed as a task graph:

5. repository analysis;
6. architecture;
7. implementation proposal generation **in parallel with** test proposal generation
   **and** security/risk review;
8. synchronization barrier: policy-controlled isolated patch application;
9. real Maven validation;
10. bounded repair and revalidation when validation fails; verified rollback to the
    baseline snapshot when the repair budget is exhausted;
11. documentation;
12. release-readiness artifact generation, then `engineering-outcome.json`;
13. pause in `AWAITING_RELEASE_APPROVAL` until the exact `engineering-outcome.json` hash
    is approved (`COMPLETED`).

The build segment is run by `TaskGraphExecutor` over the declared `BUILD_GRAPH` edge set
rather than a fixed call sequence. Each wave is the set of not-yet-run nodes whose
dependencies are complete; multi-node waves run concurrently on the bounded
`taskGraphPool` and the join is the barrier for the next wave. The executed wave list is
recorded in the `workflow.build-graph-executed` audit event.

If ambiguity analysis determines that the requirement is materially underspecified, the
design segment stops in `AWAITING_CLARIFICATION` and no patch artifacts are created. The
ambiguity decision consumes the validated `normalized-requirement.json` dimensions for
acceptance criteria, scope, API behavior, persistence, security, time boundaries,
repository target, and operational constraints; it is not based only on scenario keys or
trigger phrases.

### Clarification and revisions

`POST /api/workflows/{id}/clarifications` (operator role) records the answer against the
paused revision (`clarification-answer.json` + `workflow.clarification-received`), marks
that revision `INVALIDATED`, and opens revision N+1 whose requirement text is the original
requirement plus the answer lineage (persisted as `clarified-requirement.txt`, original
preserved). Orchestration then re-enters `startRevision` for the new revision. All agents
read the revision-scoped requirement, so the clarified revision is analysed, planned, and
built from the enriched requirement.

### Restart recovery and asynchronous execution

`agentic.orchestration.async` (default `false` for reproducible evaluation) switches
submission and change-gate resumption onto the bounded `orchestrationExecutor`; the
endpoint returns immediately and orchestration runs off the request thread.

`WorkflowRecoveryService` runs on `ApplicationReadyEvent` and every
`agentic.orchestration.recovery-interval`. It scans for workflows left `RUNNING` by an
instance that stopped mid-flight and re-drives each from its last durable checkpoint:
`engineering-outcome.json` present → release gate; a valid current-revision change
approval → re-run the build graph; `engineering-plan.json` present → re-pause at the
change gate; otherwise restart the revision from requirement analysis. Workflows that are
legitimately `AWAITING_*` a human are never touched. `startRevision` and
`resumeAfterChangeApproval` are deliberately non-transactional so every agent step commits
its own evidence and leaves a checkpoint for recovery.

The deterministic implementation and test agents generate the actual file operations that
are later applied. For the URL-shortener scenarios, the generated implementation includes a
service slice, domain record, REST controller, request DTO, and behavior tests for create,
redirect, analytics, URL validation, deactivation, and regional codes. Brownfield and
greenfield scenarios share the same schema and patch pipeline while recording different
generation modes in the generated evidence.

## Evidence

Artifacts are persisted with SHA-256 hashes and lineage. Reviewer-visible artifacts include:

- `normalized-requirement.json`
- `ambiguity-decision.json`
- `task-plan.json`
- `engineering-plan.json`
- `repository-analysis.json`
- `architecture.md`
- `implementation-proposal.json`
- `test-proposal.json`
- `patch-policy.json`
- `applied-file-operations.json`
- `unified-diff.patch`
- `source-manifest.json`
- `validation-attempt-1.json`
- `repair-proposal.json`
- `repair-diff.patch`
- `validation-attempt-2.json`
- `rollback-evidence.json` (only when the repair budget is exhausted)
- `documentation-plan.json`
- `security-risk-review.json`
- `release-readiness.json`
- `engineering-outcome.json`
- `clarified-requirement.txt` / `clarification-answer.json` (clarified revisions only)
- `safe-stop-evidence.json` (safe-stopped workflows only)

Generated files are written to isolated workflow workspaces. Workflow execution does not
edit the submitted source repository directly.

### Rollback and safe stop

Each `apply()` captures an immutable baseline snapshot beside the workspace. `rollback()`
restores the workspace from that snapshot and verifies the restored SHA-256 manifest
against the baseline; the result (`workspaceExisted`, `verified`, `restoredFiles`) is
persisted. Rollback runs automatically when the bounded repair budget is exhausted, and
`POST /api/workflows/{id}/safe-stop` (operator role, rejected for terminal workflows)
cancels a non-terminal workflow, rolls its current-revision workspace back, records
`safe-stop-evidence.json`, and transitions to `SAFE_STOPPED`.

## Governance

Local deterministic evaluation uses Basic authentication:

- `operator` submits workflows and clarifications.
- `change-approver` approves the current `engineering-plan.json` hash.
- `release-approver` approves the current `engineering-outcome.json` hash.

The approval gate rejects wrong hashes, stale hashes, missing evidence, and incorrect
roles. Approval decisions are persisted with actor, role, gate, supplied hash, canonical
reviewed-evidence hash, validity, reason, correlation ID, and timestamp.

## Idempotency And Distributed State

Workflow submission accepts `Idempotency-Key`. A replay by the same actor with the same
request returns the original workflow. Reusing the key for different content returns
HTTP 409 Problem Details.

Task rows include lease owner, lease expiry, and monotonically increasing fencing tokens.
The persistence layer exposes claim, heartbeat, and fenced-completion operations so stale
workers cannot complete a task after losing ownership.

PostgreSQL is the durable source of truth for workflows, revisions, tasks, artifacts,
validation evidence, approvals, and audit events. Because each agent step commits its own
evidence, a stopped instance leaves a consistent checkpoint that `WorkflowRecoveryService`
resumes on the next startup or scheduled scan (see *Restart recovery* above). With
`agentic.orchestration.async=true` multiple instances can accept submissions concurrently;
recovery and the fenced task leases keep a single instance authoritative for a given
revision's execution.

## URL Shortener

The URL shortener provides URL creation, redirect, expiry, deactivation, inspection, total
redirect analytics, UTC daily analytics, PostgreSQL persistence, Flyway migrations, RFC
9457 Problem Details, OpenAPI, health/readiness probes, request rate limiting with
`Retry-After`, allowed HTTP/HTTPS schemes, user-info rejection, localhost/private-address
blocking, configured blocked hosts, secure random regional short codes, collision retry,
and retention cleanup.

## Observability

`GET /actuator/prometheus` exposes bounded-label metrics. Recording rules in
`deploy/prometheus/agentic-recording-rules.yml` compute workflow success rate, retry
frequency, rollback frequency, mean time to repair, repair success rate, p95 duration,
validation failure rate, and model failure rate.

Workflow IDs, repository paths, requirements, and users are not used as metric labels.
