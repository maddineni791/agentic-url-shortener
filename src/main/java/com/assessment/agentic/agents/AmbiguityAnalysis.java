package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AmbiguityAnalysis(
    boolean requiresClarification,
    @NotNull List<String> questions,
    @NotNull List<String> blockingFields,
    @NotBlank String decisionReason
) {
}
