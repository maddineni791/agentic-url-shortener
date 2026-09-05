package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReleaseReadiness(
    boolean releaseReady,
    @NotNull List<String> evidence,
    @NotNull List<String> blockingIssues,
    @NotBlank String outcome
) {
}
