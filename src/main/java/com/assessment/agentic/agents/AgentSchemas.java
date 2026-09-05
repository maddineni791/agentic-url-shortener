package com.assessment.agentic.agents;

import java.util.LinkedHashMap;
import java.util.Map;

final class AgentSchemas {

    private AgentSchemas() {
    }

    static Map<String, String> requirement() {
        return ordered(
            "normalizedRequirement", "Normalized engineering requirement",
            "acceptanceCriteria", "Concrete acceptance criteria",
            "scope", "Implementation scope",
            "apiBehavior", "Expected REST API behavior",
            "persistenceRequirements", "Persistence requirements",
            "securityRequirements", "Security requirements",
            "timeBoundaries", "Expiry, retention, and time-zone boundaries",
            "repositoryTarget", "Target repository or scenario",
            "operationalConstraints", "Build, runtime, and deployment constraints",
            "assumptions", "Assumptions made by the agent",
            "riskLevel", "LOW, MEDIUM, or HIGH"
        );
    }

    static Map<String, String> ambiguity() {
        return ordered(
            "requiresClarification", "true or false",
            "questions", "Concrete clarification questions",
            "blockingFields", "Missing or conflicting requirement dimensions",
            "decisionReason", "Reason clarification is or is not required"
        );
    }

    static Map<String, String> repository() {
        return ordered(
            "projectStructure", "Detected project structure",
            "buildSystem", "Detected build system",
            "modules", "Relevant modules",
            "apis", "Relevant APIs and controllers",
            "persistence", "Entities, repositories, migrations, and stores",
            "tests", "Relevant test conventions",
            "changeImpact", "Likely change impact"
        );
    }

    static Map<String, String> plan() {
        return ordered(
            "tasks", "Dependency-aware task list",
            "parallelGroups", "Tasks that may run in parallel",
            "gates", "Entry, exit, policy, validation, and human gates",
            "retryPolicy", "Retry and fallback plan"
        );
    }

    static Map<String, String> architecture() {
        return ordered(
            "design", "Architecture and design decision",
            "interfaces", "APIs, services, persistence, and contracts",
            "tradeoffs", "Trade-offs and alternatives",
            "risks", "Design risks"
        );
    }

    static Map<String, String> proposal() {
        return ordered(
            "fileOperations", "Structured file operation proposals",
            "changedFiles", "Changed file list",
            "lineage", "Requirement, task, and artifact lineage"
        );
    }

    static Map<String, String> diagnosis() {
        return ordered(
            "failureClass", "Compiler, test, dependency, configuration, timeout, or infrastructure",
            "rootCause", "Likely failure cause",
            "repairGuidance", "Bounded repair guidance"
        );
    }

    static Map<String, String> documentation() {
        return ordered(
            "documents", "Documentation artifacts to update",
            "reviewerSteps", "Reviewer-visible validation steps",
            "limitations", "Assumptions, risks, and limitations"
        );
    }

    static Map<String, String> risk() {
        return ordered(
            "riskLevel", "LOW, MEDIUM, or HIGH",
            "findings", "Security and production risk findings",
            "policyBlocks", "Policy blocks before mutation or release",
            "mitigations", "Mitigations and owner responsibilities"
        );
    }

    static Map<String, String> release() {
        return ordered(
            "releaseReady", "true or false",
            "evidence", "Evidence reviewed",
            "blockingIssues", "Blocking issues",
            "outcome", "Release readiness outcome"
        );
    }

    private static Map<String, String> ordered(String... values) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            fields.put(values[i], values[i + 1]);
        }
        return fields;
    }
}
