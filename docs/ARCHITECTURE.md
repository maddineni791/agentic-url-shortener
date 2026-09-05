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

The audit stream records `patch.applied` or `patch.policy-rejected`. Validation still does
not execute the generated repository build; fixed Maven validation, failure diagnosis,
repair, retry, and rollback are the next checkpoint.
