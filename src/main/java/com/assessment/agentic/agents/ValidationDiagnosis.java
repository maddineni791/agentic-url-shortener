package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;

public record ValidationDiagnosis(
    @NotBlank String failureClass,
    @NotBlank String rootCause,
    @NotBlank String repairGuidance
) {
}
