# Production Deployment

The repository includes Docker packaging and a Compose topology for local review. Production
deployment requires platform operations outside this codebase.

## Provided By This Repository

- Java 21 executable Spring Boot JAR.
- Dockerfile with non-root runtime user.
- Docker Compose with PostgreSQL, two orchestrator instances, shared workspace volume, and
  Prometheus.
- Environment-only database and model-provider configuration.
- Local Basic authentication for deterministic evaluation.
- Optional OpenAI Responses API provider via environment variables.
- Prometheus metrics and recording rules.

## Operator Responsibilities

- Provision OIDC/JWT identity provider, issuer, audience, signing keys, and role mapping.
- Issue and rotate TLS certificates.
- Create external managed secrets for database and model-provider credentials.
- Configure DNS, ingress, global rate limits, and WAF rules.
- Enforce network egress protections for generated workload validation.
- Operate PostgreSQL backups, high availability, and retention.
- Plan and operate actual multi-region deployment.

The application does not claim those external controls are automatically provisioned.
