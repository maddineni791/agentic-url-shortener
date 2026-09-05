package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;

public record RepositoryAnalysis(
    @NotBlank String projectStructure,
    @NotBlank String buildSystem,
    @NotBlank String modules,
    @NotBlank String apis,
    @NotBlank String persistence,
    @NotBlank String tests,
    @NotBlank String changeImpact
) {
}
