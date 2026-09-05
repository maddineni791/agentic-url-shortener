# Observability

The platform exposes Micrometer metrics at `GET /actuator/prometheus`.

Metric labels are intentionally bounded. They use scenario catalog keys, task types,
outcomes, providers, token direction, and validation failure classes. Workflow IDs,
requirements, repository paths, users, and other high-cardinality values are not metric
labels.

## Emitted Metrics

- `agentic_workflows_submitted_total`
- `agentic_workflows_terminal_total`
- `agentic_tasks_total`
- `agentic_task_duration_seconds`
- `agentic_validation_attempts_total`
- `agentic_validation_duration_seconds`
- `agentic_repair_attempts_total`
- `agentic_repair_duration_seconds`
- `agentic_rollbacks_total`
- `agentic_idempotency_replays_total`
- `agentic_model_calls_total`
- `agentic_model_latency_seconds`
- `agentic_model_tokens_total`

## Recording Rules

Prometheus recording rules live in
`deploy/prometheus/agentic-recording-rules.yml`.

The file defines computed indicators for:

- workflow success rate;
- retry frequency per workflow;
- rollback frequency per workflow;
- mean time to repair;
- repair success rate;
- p95 workflow duration proxy;
- validation failure rate;
- model failure rate.

These are derived from counters and timers instead of claiming raw counters are the final
indicators.

## Reviewer Checks

Run:

```powershell
.\mvnw.cmd clean verify
```

The API test suite submits workflows, drives exact-evidence approval, and asserts that
Prometheus exposes workflow, model, and validation metrics.
