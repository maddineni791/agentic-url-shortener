# Engineering Outcome

The implemented platform demonstrates the assessment lifecycle:

requirement -> requirement analysis -> ambiguity decision -> repository-aware plan ->
generated implementation and tests -> governed isolated mutation -> real Maven validation ->
repair when required -> documentation and risk review -> exact-evidence release approval.

## Delivered Artifacts

- Runnable Spring Boot REST API.
- Functional URL shortener backed by PostgreSQL/Flyway.
- Provider-neutral model and specialized agent contracts.
- Deterministic provider requiring no API key.
- Optional OpenAI Responses API provider.
- Isolated workspace patch application with policy checks and SHA-256 evidence.
- Real Maven validation with bounded logs and failure classification.
- Deterministic repair scenario.
- Exact-evidence approval gates.
- Idempotency keys and fenced task-lease persistence.
- Prometheus metrics and recording rules.
- Dockerfile, Docker Compose, and GitHub Actions CI.

## Validation Evidence

The final verification command is:

```powershell
.\mvnw.cmd clean verify
```

Expected result: 42 tests passing, Flyway migration validation, JAR packaging, JaCoCo
reporting, and JaCoCo threshold check.

## Assumptions And Limits

The deterministic provider is intentionally template-based so the assessment can run
without external credentials. Production OIDC/JWT provisioning, TLS issuance, external
secrets, DNS, WAF, global ingress rate limits, and actual multi-region deployment are
operator responsibilities.
