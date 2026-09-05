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
- `validation`: fixed Maven Wrapper validation with timeout, bounded logs, credential
  stripping, exit code capture, and failure classification.
- `persistence`: Spring JDBC stores backed by PostgreSQL and Flyway.
- `urlshortener`: functional URL shortener used as the concrete engineering target.
- `observability`: Micrometer metrics, Prometheus scrape endpoint, and recording rules.

## Workflow Lifecycle

`POST /api/workflows` executes:

1. requirement interpretation;
2. ambiguity analysis;
3. dependency-aware task planning;
4. repository analysis;
5. architecture;
6. implementation proposal generation;
7. test proposal generation;
8. policy-controlled isolated patch application;
9. real Maven validation;
10. bounded repair and revalidation when validation fails;
11. documentation and risk review;
12. release-readiness artifact generation;
13. exact-hash human approval.

If ambiguity analysis determines that the requirement is materially underspecified, the
workflow transitions to `AWAITING_CLARIFICATION` and no patch artifacts are created.

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
- `validation-attempt-2.json`
- `documentation-plan.json`
- `security-risk-review.json`
- `release-readiness.json`
- `engineering-outcome.json`

Generated files are written to isolated workflow workspaces. Workflow execution does not
edit the submitted source repository directly.

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
workers cannot complete a task after losing ownership. The current runner executes
synchronously inside the handling app instance while persisting the lease/fencing state
needed for distributed execution.

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
