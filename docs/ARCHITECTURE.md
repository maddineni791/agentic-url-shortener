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
`design-change`, `implement-change`, `generate-tests`, `synchronize-patch`,
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
