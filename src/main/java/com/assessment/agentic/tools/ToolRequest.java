package com.assessment.agentic.tools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ToolRequest(
    @NotBlank String workflowId,
    int revision,
    @NotBlank String capability,
    @NotNull Map<String, String> arguments
) {
}
