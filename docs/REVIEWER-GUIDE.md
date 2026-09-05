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

The current workspace does not include `maven-wrapper.jar`; generate it with Maven or allow
the wrapper jar to be added in a later environment where Maven/network access is available.
