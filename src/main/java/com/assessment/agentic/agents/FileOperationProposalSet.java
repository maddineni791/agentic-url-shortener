package com.assessment.agentic.agents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FileOperationProposalSet(
    @Valid @NotNull List<FileOperationProposal> fileOperations,
    @NotNull List<String> changedFiles,
    @NotNull List<String> lineage
) {
}
