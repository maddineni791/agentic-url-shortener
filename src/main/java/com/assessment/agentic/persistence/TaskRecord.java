package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record TaskRecord(
    UUID id,
    UUID workflowId,
    UUID revisionId,
    String taskKey,
    String taskType,
    TaskStatus status,
    String dependsOnJson,
    int attemptCount,
    int maxAttempts,
    int timeoutSeconds,
    String leaseOwner,
    Instant leaseExpiresAt,
    long fencingToken,
    int contextVersion,
    Instant createdAt,
    Instant updatedAt
) {
}
