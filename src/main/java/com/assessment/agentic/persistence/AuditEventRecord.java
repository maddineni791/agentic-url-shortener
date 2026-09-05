package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record AuditEventRecord(
    UUID id,
    UUID workflowId,
    UUID revisionId,
    UUID taskId,
    String eventType,
    String actor,
    String correlationId,
    String redactedPayload,
    String originalPayloadSha256,
    Instant createdAt
) {
}
