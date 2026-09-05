package com.assessment.agentic.agents;

import jakarta.validation.constraints.NotBlank;

public record FileOperationProposal(
    @NotBlank String operationType,
    @NotBlank String normalizedRelativePath,
    @NotBlank String completeProposedContent,
    String expectedCurrentSha256,
    @NotBlank String reason,
    @NotBlank String requirementId,
    @NotBlank String taskId,
    @NotBlank String artifactLineage
) {
}
