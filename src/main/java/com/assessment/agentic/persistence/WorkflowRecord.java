package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record WorkflowRecord(
    UUID id,
    String externalId,
    String scenarioKey,
    WorkflowStatus status,
    String originalRequirement,
    int currentRevision,
    Instant createdAt,
    Instant updatedAt
) {
}
