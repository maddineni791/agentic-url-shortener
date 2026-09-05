package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record RevisionRecord(
    UUID id,
    UUID workflowId,
    int revisionNumber,
    RevisionStatus status,
    String requirementHash,
    UUID parentRevisionId,
    Instant invalidatedAt,
    String invalidationReason,
    Instant createdAt
) {
}
