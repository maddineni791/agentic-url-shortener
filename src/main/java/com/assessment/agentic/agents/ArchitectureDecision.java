package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;

public record ArchitectureDecision(
    @NotBlank String design,
    @NotBlank String interfaces,
    @NotBlank String tradeoffs,
    @NotBlank String risks
) {
}
