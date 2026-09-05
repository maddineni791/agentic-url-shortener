# Manual Acceptance

Use `docs/REVIEWER-GUIDE.md` as the copy-and-paste acceptance script.

The required manual checks are:

- local build verification with `.\mvnw.cmd clean verify`;
- deterministic startup against Docker Desktop PostgreSQL;
- health, readiness, OpenAPI, Swagger UI, and Prometheus endpoints;
- URL creation, redirect, inspection, analytics, validation rejection, and deactivation;
- agentic workflow submission;
- idempotent replay;
- artifact, audit, policy, and validation evidence inspection;
- exact-hash release approval;
- deliberate validation failure and repair;
- ambiguous requirement pause;
- Docker Compose config validation and two-instance startup.
