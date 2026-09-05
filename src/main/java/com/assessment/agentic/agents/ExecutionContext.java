package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ExecutionContext(
    @NotBlank String workflowId,
    int revision,
    @NotBlank String requirement,
    @NotNull Map<String, String> artifacts
) {
}
