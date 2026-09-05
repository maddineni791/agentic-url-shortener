package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SecurityRiskReview(
    @NotBlank String riskLevel,
    @NotNull List<String> findings,
    @NotNull List<String> policyBlocks,
    @NotNull List<String> mitigations
) {
}
