package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRecord(
    UUID id,
    UUID workflowId,
    UUID revisionId,
    String gate,
    String actor,
    String role,
    String decision,
    String reason,
    String requiredArtifactNames,
    String suppliedHashes,
    String canonicalReviewedEvidenceHash,
    String correlationId,
    boolean valid,
    String invalidationReason,
    Instant decidedAt
) {
}
