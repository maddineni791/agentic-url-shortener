package com.assessment.agentic.validation;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ValidationContext(
    @NotBlank String workflowId,
    int revision,
    Map<String, String> artifacts
) {
}
