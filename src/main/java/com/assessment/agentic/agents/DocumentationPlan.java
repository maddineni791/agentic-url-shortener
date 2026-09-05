package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DocumentationPlan(
    @NotNull List<String> documents,
    @NotNull List<String> reviewerSteps,
    @NotNull List<String> limitations
) {
}
