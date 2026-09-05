# Reviewer Guide

This guide will be expanded at each checkpoint with exact commands, credentials, API calls,
and expected evidence.

## Checkpoint 1

Expected behavior:

- The repository is a Java 21 Spring Boot project.
- The application exposes `GET /api/platform`.
- The traceability matrix exists and identifies incomplete requirements honestly.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

## Checkpoint 2

Expected behavior:

- Flyway applies the durable workflow schema.
- The persistence adapter can create and read workflows, revisions, tasks, artifacts, and
  audit events.
- Audit payloads redact common secret assignments while retaining an original payload hash.

Validation command:

```powershell
.\mvnw.cmd clean verify
```

Expected test result for checkpoint 2: 4 tests, 0 failures, 0 errors, 0 skipped.
