package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
    String actor,
    String idempotencyKey,
    String requestHash,
    UUID workflowId,
    int responseStatus,
    Instant createdAt
) {
}
