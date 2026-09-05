package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RequirementAnalysis(
    @NotBlank String normalizedRequirement,
    @NotNull List<String> acceptanceCriteria,
    @NotBlank String scope,
    @NotBlank String apiBehavior,
    @NotBlank String persistenceRequirements,
    @NotBlank String securityRequirements,
    @NotBlank String timeBoundaries,
    @NotBlank String repositoryTarget,
    @NotBlank String operationalConstraints,
    @NotNull List<String> assumptions,
    @NotBlank String riskLevel
) {
}
