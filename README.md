# Agentic-Proficient Software Engineer Platform

This repository contains a Spring Boot based agentic SDLC orchestration platform for the
Agentic-Proficient Software Engineer assessment. The platform will expose REST APIs that
drive a requirement-to-code-to-test lifecycle against isolated repository workspaces, using
a functional URL shortener as the concrete engineering target.

The implementation is being developed in reviewable checkpoints. The assignment PDF is the
authoritative requirements source; reviewer-approved projects are used only as quality
references and are not copied.

## Current Checkpoint

Checkpoint 1 establishes the runnable Java 21/Spring Boot foundation and the initial
traceability matrix. Later checkpoints add persistence, orchestration, agents, governed
patch application, validation, repair, distributed execution, URL-shortener behavior, and
reviewer evidence.

## Local Development

Java 21 is required.

```powershell
.\mvnw.cmd clean verify
```

The default runtime profile expects PostgreSQL at
`jdbc:postgresql://localhost:5432/agentic` with username/password `agentic`/`agentic`.
Tests use an H2 database in PostgreSQL compatibility mode.

On Unix-like shells:

```bash
./mvnw clean verify
```

## Documentation

- [Traceability](docs/TRACEABILITY.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Reviewer Guide](docs/REVIEWER-GUIDE.md)

## Deterministic Local Roles

Checkpoint 3 includes local Basic authentication for platform APIs:

| Username | Password | Role |
| -------- | -------- | ---- |
| `operator` | `operator-pass` | `OPERATOR` |
| `change-approver` | `change-pass` | `CHANGE_APPROVER` |
| `release-approver` | `release-pass` | `RELEASE_APPROVER` |

OpenAPI is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
