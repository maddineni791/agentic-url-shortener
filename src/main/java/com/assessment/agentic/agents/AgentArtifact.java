package com.assessment.agentic.agents;

import com.assessment.agentic.persistence.Hashing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentArtifact(
    @NotBlank String name,
    @NotBlank String type,
    @NotBlank String content,
    @NotBlank String sha256,
    @NotBlank String producingTaskId,
    @NotNull AgentRole producingAgent
) {
    public static AgentArtifact of(String name, String type, String content, AgentTask task) {
        return new AgentArtifact(name, type, content, Hashing.sha256(content), task.id(), task.agent());
    }
}
