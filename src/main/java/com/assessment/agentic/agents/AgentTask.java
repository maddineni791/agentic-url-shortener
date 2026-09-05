package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AgentTask(
    @NotBlank String id,
    @NotNull AgentRole agent,
    @NotBlank String goal,
    @NotNull List<String> dependencies,
    @NotNull Map<String, String> inputs
) {
}
