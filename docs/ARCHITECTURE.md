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
