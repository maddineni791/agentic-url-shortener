package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record ArtifactRecord(
    UUID id,
    UUID workflowId,
    UUID revisionId,
    UUID producingTaskId,
    String name,
    String mediaType,
    String sha256,
    String content,
    String lineageJson,
    Instant createdAt
) {
}
