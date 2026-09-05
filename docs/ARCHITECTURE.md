# Architecture

This document will evolve with the implementation. The intended product is a Spring Boot
agentic SDLC platform that accepts software-engineering requirements, analyzes ambiguity,
inspects repositories through bounded tools, creates a dependency-aware execution graph,
generates implementation and test proposals through provider-neutral agents, applies
approved patches in isolated workspaces, validates with fixed build capabilities, performs
bounded repair, and exposes durable evidence for human approval.

## Checkpoint 2 Persistence

The platform now has a durable state foundation managed by Flyway. The schema includes
workflows, workflow revisions, workflow tasks, artifacts, validation attempts, approvals,
and audit events. The Java adapter uses Spring JDBC rather than hidden ORM state so later
orchestration code can make state transitions, leases, fencing tokens, and approval
invalidation explicit.

Artifact and audit payloads are hash-linked with SHA-256. Audit events persist a redacted
payload plus a hash of the bounded original payload, which lets reviewers verify lineage
without storing secrets in clear text.

Later checkpoints will connect these tables to the orchestration graph, repository sandbox,
validation and repair loop, distributed workers, and public REST APIs.

## Checkpoint 3 API And Security

The platform now exposes the first secured REST API surface:

- `GET /api/platform`
- `GET /api/scenarios`
- `POST /api/workflows`
- `GET /api/workflows/{workflowId}`
- `GET /api/workflows/{workflowId}/tasks`
- `POST /api/workflows/{workflowId}/clarifications`
- `POST /api/workflows/{workflowId}/approvals/change`
- `POST /api/workflows/{workflowId}/approvals/release`
- `POST /api/workflows/{workflowId}/safe-stop`
- `GET /api/workflows/{workflowId}/revisions`
- `GET /api/workflows/{workflowId}/artifacts`
- `GET /api/workflows/{workflowId}/artifacts/{name}`
- `GET /api/workflows/{workflowId}/policies`
- `GET /api/workflows/{workflowId}/approvals`
- `GET /api/workflows/{workflowId}/audit-events`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`

The checkpoint intentionally keeps orchestration lists empty until the graph, policies, and
evidence producers exist in later checkpoints. Workflow submission is real: it creates a
durable workflow, revision 1, and a redacted audit event.

Local deterministic evaluation uses Basic authentication with three roles: `OPERATOR`,
`CHANGE_APPROVER`, and `RELEASE_APPROVER`. Problem responses use RFC 9457
`application/problem+json` structures with stable `code` and `correlationId` fields.

## Checkpoint 4 Model Abstraction

Agents will call models through a provider-neutral contract:

- `ModelRequest` declares the SDLC capability, agent name, schema name, bounded prompt,
  required fields, and context.
- `ModelResult` returns provider, model, schema name, structured fields, raw bounded output,
  character counts, latency, and optional token usage.
- `ModelGateway` validates requests and required result fields, redacts common secret
  assignments before invocation, and enforces configured input/output bounds.

Two providers are available:

- `DETERMINISTIC`, the default keyless provider, returns repeatable structured output.
- `OPENAI`, an optional Responses API provider selected with `AGENTIC_MODEL_PROVIDER=openai`
  and configured through `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL`.

The model layer does not provide filesystem access, command execution, approval authority,
or deployment authority. Later checkpoints will bind specialized agents to this contract.

## Checkpoint 5 Agent Execution Plane

The platform now has the executor-facing agent layer that was missing in the rejected
control-plane interpretation. Concrete agent classes implement the verbs the orchestrator
will invoke:

- requirement interpretation;
- ambiguity and clarification analysis;
- repository analysis;
- dependency-aware task decomposition;
- architecture;
- implementation proposal generation;
- test proposal generation;
- validation diagnosis;
- repair proposal generation;
- documentation planning;
- security and risk review;
- release readiness review.

Every specialized agent calls `ModelGateway` with a named schema and required fields, maps
the provider-neutral result into a typed Java record, validates that record, and emits a
hash-linked `AgentArtifact`. The deterministic provider remains keyless, but it uses the
same contracts as the OpenAI provider.

`TaskDecompositionAgent` produces executor tasks such as `analyze-repository`,
`design-change`, `implement-change`, `generate-tests`, `apply-generated-patch`,
`security-risk-review`, and `release-readiness`, including dependencies and parallel work
groups. `ImplementationAgent`, `TestGenerationAgent`, and `RepairAgent` produce structured
`FileOperationProposal` objects with operation type, normalized relative path, complete
content, optimistic-lock hash where needed, reason, requirement identifier, task
identifier, and lineage. Later checkpoints will persist those artifacts, run policy
checks, apply them inside isolated repository copies, and validate them with fixed Maven
commands.

Ambiguity detection is deliberately tied to requirement dimensions rather than a scenario
enum. The ambiguity agent checks missing or conflicting API behavior, persistence,
security, and time-boundary dimensions and returns concrete clarification questions before
source mutation is allowed.

The `EngineeringTool` and `ArtifactValidator` interfaces are controlled extension points
for bounded repository reads, patch application, validation, evidence checks, retry,
fallback, and rollback.

## Checkpoint 6 Durable Agent Runner

Workflow submission now invokes the agent execution plane. `WorkflowOrchestrator` creates
durable tasks, claims them, marks them running, invokes the matching specialized agent,
persists generated artifacts, appends audit events, and advances workflow status based on
observed agent output.

The first branch is intentionally decisive:

- every workflow runs `understand-requirement`;
- every workflow then runs `analyze-ambiguity`;
- if the ambiguity artifact says clarification is required, the workflow transitions to
  `AWAITING_CLARIFICATION` and no implementation/test proposal task is created;
- otherwise the planner runs, planned task records are stored, engineering agents produce
  proposal/risk/release artifacts, and the workflow transitions to
  `AWAITING_RELEASE_APPROVAL`.

The REST evidence APIs now expose real durable data:

- `GET /api/workflows/{workflowId}/tasks` returns generated task keys, agent types,
  statuses, attempts, and dependencies;
- `GET /api/workflows/{workflowId}/artifacts` returns current-revision artifact summaries
  with hashes and producing task identifiers;
- `GET /api/workflows/{workflowId}/artifacts/{name}` returns bounded artifact content;
- `GET /api/workflows/{workflowId}/audit-events` returns task claim/completion and workflow
  transition events.

This checkpoint still does not mutate a submitted repository. Repository isolation,
policy-controlled patch application, validation, repair, rollback, distributed leases, and
restart recovery remain later checkpoints.

## Checkpoint 7 Governed Isolated Patch Application

Generated implementation and test proposals now flow through a policy-controlled repository
mutation step. The orchestrator reads `implementation-proposal.json` and
`test-proposal.json`, combines the proposed file operations, and sends them to
`IsolatedRepositoryService`.

The repository tool creates a fresh isolated workspace under the configured
`agentic.workspace.root` directory, which defaults to `target/agent-workspaces`. It seeds a
minimal baseline, applies only policy-accepted operations, and never writes to the
submitted source repository.

Patch policy currently enforces:

- normalized relative paths with forward slashes;
- absolute-path and traversal rejection;
- duplicate-operation rejection;
- approved file extensions;
- configured operation-count and file-size limits;
- expected SHA-256 hashes for update and delete operations.

The patch step persists reviewer-visible evidence:

- `patch-policy.json`;
- `applied-file-operations.json`;
- `unified-diff.patch`;
- `source-manifest.json`.

The audit stream records `patch.applied` or `patch.policy-rejected`.

## Checkpoint 8 Real Validation And Repair

Applied workspaces are now validated with a fixed command owned by the platform:
`mvnw.cmd clean test`. The command is executed only inside the isolated workspace, uses the
workspace Maven Wrapper, has a timeout, strips model-provider credentials from the child
environment, captures bounded stdout and stderr, records duration and exit code, and
classifies failures as compiler, test, dependency, configuration, timeout, infrastructure,
or none.

Validation evidence is durable:

- `validation-attempt-1.json`;
- `validation-attempt-2.json` when repair is invoked;
- rows in `validation_attempts`;
- `GET /api/workflows/{workflowId}/validation-attempts`.

The deterministic `repair-demonstration` path now deliberately generates a Java compiler
failure in the model-produced implementation proposal. The validator captures the actual
Maven compiler failure, the orchestrator invokes `RepairAgent` with the failed path,
current file hash, failure class, and bounded logs, and the repair agent returns a
corrected structured file-operation proposal. That proposal flows through the same patch
policy and isolated workspace application logic, then Maven validation runs again. The
repair budget is currently bounded to one repair attempt.

Rollback is identified when repair budget is exhausted, but verified rollback execution is
still scheduled for the next repository-safety checkpoint.

## Checkpoint 9 URL Shortener Core

The platform now includes the concrete URL-shortener product slice used by the assessment
scenarios. The bounded context owns its controller, service, repository, safety validator,
short-code generator, configuration properties, and Flyway schema.

Core APIs:

- `POST /api/urls` creates a short URL;
- `GET /r/{code}` redirects with `302 Found` and `Location`;
- `GET /api/urls/{code}` inspects active state, expiry, target URL, and redirect count;
- `PATCH /api/urls/{code}/deactivate` deactivates a short URL.

Persistence is backed by `short_urls` and `redirect_events`. Redirects update
`redirect_count` and persist an event. URL validation currently allows only HTTP/HTTPS,
requires a host, rejects user-info, and returns RFC 9457 Problem Details through the shared
API exception handler.

Commit 10 will deepen this product area with private-address blocking, configured blocked
hosts, rate limiting with `Retry-After`, regional code prefixes, UTC daily analytics API,
collision/concurrency tests, and retention cleanup.

## Checkpoint 10 URL Shortener Production Controls

The URL-shortener bounded context now includes the production controls expected by the
assessment prototype:

- request rate limiting for URL creation with HTTP 429 and `Retry-After`;
- HTTP/HTTPS-only URL validation;
- URL user-info rejection;
- localhost, loopback, link-local, RFC 1918 private IPv4, and wildcard address blocking;
- configurable blocked hosts;
- secure random short-code generation with configurable regional prefixes;
- collision retry during short-code allocation;
- total redirect analytics and UTC daily analytics;
- configurable redirect-event and inactive/expired URL retention;
- scheduled cleanup plus an explicit cleanup API for reviewer evidence.

Additional APIs:

- `GET /api/urls/{code}/analytics`;
- `POST /api/urls/cleanup`.

Commit 10 keeps production controls inside deterministic Java policy and repository code,
separate from agent reasoning. The remaining URL-shortener depth is mostly documentation
and optional PostgreSQL container acceptance; the next checkpoints return to platform
scenarios, governance, metrics, distributed recovery, packaging, and final acceptance.

## Checkpoint 11 Idempotency And Lease Fencing

Workflow submission now supports an `Idempotency-Key` header. The platform stores the
authenticated actor, key, normalized request hash, response status, and workflow ID in
PostgreSQL through Flyway migration `V3__idempotency_and_leases.sql`. Replaying the same
key with the same request returns the original workflow instead of creating duplicate
engineering work. Reusing the same key for different request content returns HTTP 409
Problem Details with code `CONFLICT`.

The task store now exposes database-backed lease primitives for the distributed worker
model required by the assessment:

- `claimTask` assigns an owner only when the task is unleased or expired and increments a
  monotonic fencing token;
- `heartbeatTaskLease` extends only the current owner/token pair;
- `completeTaskWithFence` rejects stale completions whose owner or fencing token no longer
  matches.

Task status APIs now include lease owner, lease expiry, and fencing token so reviewers can
inspect distributed-execution state. Commit 11 proves the persistence and API contracts;
automatic background recovery and multi-instance scheduled claims are still planned for the
distributed recovery checkpoint.

## Checkpoint 12 Governance And Observability

The approval endpoints now enforce exact current-revision evidence:

- `POST /api/workflows/{workflowId}/approvals/change` requires the current
  `engineering-plan.json` SHA-256 hash and the `CHANGE_APPROVER` role;
- `POST /api/workflows/{workflowId}/approvals/release` requires the current
  `engineering-outcome.json` SHA-256 hash and the `RELEASE_APPROVER` role;
- invented, stale, or unrelated hashes produce HTTP 409 Problem Details and a persisted
  rejected approval record;
- successful release approval transitions the workflow to `COMPLETED`.

The orchestrator now emits canonical `engineering-plan.json` and
`engineering-outcome.json` artifacts so human gates reference stable evidence names rather
than informal lifecycle artifacts.

Micrometer metrics are exposed at `GET /actuator/prometheus`. Metric labels are bounded to
scenario keys, task types, outcomes, providers, token direction, and validation failure
classes. Recording rules in `deploy/prometheus/agentic-recording-rules.yml` define the
computed reliability indicators required by the assessment.
