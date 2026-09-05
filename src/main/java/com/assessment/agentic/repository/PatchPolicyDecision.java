package com.assessment.agentic.repository;

import java.util.List;

public record PatchPolicyDecision(
    boolean allowed,
    List<String> acceptedPaths,
    List<String> rejectedPaths,
    List<String> reasons
) {
}
