package com.assessment.agentic.validation;

public record BuildValidationEvidence(
    int attemptNumber,
    String commandName,
    Integer exitCode,
    long durationMillis,
    boolean timedOut,
    String failureClassification,
    String stdoutExcerpt,
    String stderrExcerpt
) {
    public boolean successful() {
        return !timedOut && exitCode != null && exitCode == 0;
    }
}
