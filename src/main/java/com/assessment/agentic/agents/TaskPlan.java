package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TaskPlan(
    @NotNull List<PlannedTask> tasks,
    @NotNull List<String> parallelGroups,
    @NotNull List<String> gates,
    @NotBlank String retryPolicy
) {
    public record PlannedTask(
        @NotBlank String id,
        @NotNull AgentRole agent,
        @NotBlank String goal,
        @NotNull List<String> dependencies
    ) {
    }
}
