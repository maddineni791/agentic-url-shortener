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
- exact-hash **change** approval, and rejection of a wrong plan hash;
- exact-hash **release** approval, and rejection of an invented outcome hash;
- deliberate validation failure and repair (fail -> repair -> pass);
- ambiguous requirement pause, then clarification -> revision 2 -> resume;
- safe stop with verified workspace rollback (`safe-stop-evidence.json`);
- restart recovery of an interrupted `RUNNING` workflow (`workflow.recovery-resumed`);
- optional asynchronous submission (`AGENTIC_ORCHESTRATION_ASYNC=true`);
- Docker Compose config validation and two-instance startup.
