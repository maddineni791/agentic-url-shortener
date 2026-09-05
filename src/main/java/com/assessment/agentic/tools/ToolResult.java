package com.assessment.agentic.tools;

import jakarta.validation.constraints.NotBlank;

public record ToolResult(
    boolean successful,
    @NotBlank String evidence,
    @NotBlank String boundedOutput
) {
}
