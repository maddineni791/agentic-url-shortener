package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AgentExecutionResult<T>(
    @NotBlank String taskId,
    @NotNull AgentRole agent,
    @Valid @NotNull T output,
    @Valid @NotNull List<AgentArtifact> artifacts,
    @NotNull ModelResult modelResult
) {
}
