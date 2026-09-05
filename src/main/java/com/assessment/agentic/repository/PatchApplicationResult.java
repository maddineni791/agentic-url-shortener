package com.assessment.agentic.repository;

import java.util.List;

public record PatchApplicationResult(
    String workspacePath,
    String baselineManifestHash,
    String appliedManifestHash,
    List<String> changedFiles,
    String unifiedDiff,
    PatchPolicyDecision policyDecision
) {
}
