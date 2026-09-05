package com.assessment.agentic.persistence;

import java.time.Instant;
import java.util.UUID;

public record ValidationAttemptRecord(
    UUID id,
    UUID workflowId,
    UUID revisionId,
    UUID taskId,
    int attemptNumber,
    String commandName,
    Integer exitCode,
    long durationMillis,
    boolean timedOut,
    String failureClassification,
    String stdoutExcerpt,
    String stderrExcerpt,
    Instant createdAt
) {
}
