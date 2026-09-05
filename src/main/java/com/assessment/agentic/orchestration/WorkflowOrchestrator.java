package com.assessment.agentic.orchestration;

import com.assessment.agentic.agents.AgentArtifact;
import com.assessment.agentic.agents.AgentExecutionResult;
import com.assessment.agentic.agents.AgentRole;
import com.assessment.agentic.agents.AgentTask;
import com.assessment.agentic.agents.AmbiguityAnalysis;
import com.assessment.agentic.agents.AmbiguityClarificationAgent;
import com.assessment.agentic.agents.ArchitectureAgent;
import com.assessment.agentic.agents.DocumentationAgent;
import com.assessment.agentic.agents.ExecutionContext;
import com.assessment.agentic.agents.FileOperationProposalSet;
import com.assessment.agentic.agents.ImplementationAgent;
import com.assessment.agentic.agents.RepairAgent;
import com.assessment.agentic.agents.ReleaseReadinessAgent;
import com.assessment.agentic.agents.RepositoryAnalysisAgent;
import com.assessment.agentic.agents.RequirementUnderstandingAgent;
import com.assessment.agentic.agents.SecurityRiskReviewAgent;
import com.assessment.agentic.agents.TaskDecompositionAgent;
import com.assessment.agentic.agents.TaskPlan;
import com.assessment.agentic.agents.TestGenerationAgent;
import com.assessment.agentic.persistence.ArtifactRecord;
import com.assessment.agentic.persistence.RevisionRecord;
import com.assessment.agentic.persistence.TaskRecord;
import com.assessment.agentic.persistence.TaskStatus;
import com.assessment.agentic.persistence.WorkflowRecord;
import com.assessment.agentic.persistence.WorkflowStateStore;
import com.assessment.agentic.persistence.WorkflowStatus;
import com.assessment.agentic.observability.WorkflowMetrics;
import com.assessment.agentic.repository.IsolatedRepositoryService;
import com.assessment.agentic.repository.PatchApplicationResult;
import com.assessment.agentic.validation.BuildValidationEvidence;
import com.assessment.agentic.validation.FixedMavenValidationRunner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowOrchestrator {

    private final WorkflowStateStore store;
    private final RequirementUnderstandingAgent requirementAgent;
    private final AmbiguityClarificationAgent ambiguityAgent;
    private final TaskDecompositionAgent plannerAgent;
    private final RepositoryAnalysisAgent repositoryAgent;
    private final ArchitectureAgent architectureAgent;
    private final ImplementationAgent implementationAgent;
    private final TestGenerationAgent testGenerationAgent;
    private final RepairAgent repairAgent;
    private final DocumentationAgent documentationAgent;
    private final SecurityRiskReviewAgent securityRiskReviewAgent;
    private final ReleaseReadinessAgent releaseReadinessAgent;
    private final IsolatedRepositoryService isolatedRepositoryService;
    private final FixedMavenValidationRunner validationRunner;
    private final ObjectMapper objectMapper;
    private final WorkflowMetrics workflowMetrics;

    public WorkflowOrchestrator(
        WorkflowStateStore store,
        RequirementUnderstandingAgent requirementAgent,
        AmbiguityClarificationAgent ambiguityAgent,
        TaskDecompositionAgent plannerAgent,
        RepositoryAnalysisAgent repositoryAgent,
        ArchitectureAgent architectureAgent,
        ImplementationAgent implementationAgent,
        TestGenerationAgent testGenerationAgent,
        RepairAgent repairAgent,
        DocumentationAgent documentationAgent,
        SecurityRiskReviewAgent securityRiskReviewAgent,
        ReleaseReadinessAgent releaseReadinessAgent,
        IsolatedRepositoryService isolatedRepositoryService,
        FixedMavenValidationRunner validationRunner,
        ObjectMapper objectMapper,
        WorkflowMetrics workflowMetrics
    ) {
        this.store = store;
        this.requirementAgent = requirementAgent;
        this.ambiguityAgent = ambiguityAgent;
        this.plannerAgent = plannerAgent;
        this.repositoryAgent = repositoryAgent;
        this.architectureAgent = architectureAgent;
        this.implementationAgent = implementationAgent;
        this.testGenerationAgent = testGenerationAgent;
        this.repairAgent = repairAgent;
        this.documentationAgent = documentationAgent;
        this.securityRiskReviewAgent = securityRiskReviewAgent;
        this.releaseReadinessAgent = releaseReadinessAgent;
        this.isolatedRepositoryService = isolatedRepositoryService;
        this.validationRunner = validationRunner;
        this.objectMapper = objectMapper;
        this.workflowMetrics = workflowMetrics;
    }

    @Transactional
    public void startRevision(WorkflowRecord workflow, RevisionRecord revision, String actor, String correlationId) {
        store.updateWorkflowStatus(workflow.id(), WorkflowStatus.RUNNING);
        ExecutionContext context = new ExecutionContext(workflow.id().toString(), revision.revisionNumber(), workflow.originalRequirement(), new LinkedHashMap<>());
        TaskRecord requirementTask = run("understand-requirement", AgentRole.REQUIREMENT_INTERPRETER, List.of(), workflow, revision, actor, correlationId,
            task -> requirementAgent.execute(task, context));
        TaskRecord ambiguityTask = run("analyze-ambiguity", AgentRole.AMBIGUITY_ANALYST, List.of(requirementTask.taskKey()), workflow, revision, actor, correlationId,
            task -> ambiguityAgent.execute(task, contextWithArtifacts(workflow, revision)));

        ArtifactRecord ambiguityArtifact = store.findArtifact(revision.id(), "ambiguity-decision.json").orElseThrow();
        AmbiguityAnalysis ambiguity = read(ambiguityArtifact.content(), AmbiguityAnalysis.class);
        if (ambiguity.requiresClarification()) {
            store.updateWorkflowStatus(workflow.id(), WorkflowStatus.AWAITING_CLARIFICATION);
            store.appendAuditEvent(workflow.id(), revision.id(), ambiguityTask.id(), "workflow.awaiting-clarification", actor, correlationId,
                write(Map.of("questions", ambiguity.questions(), "blockingFields", ambiguity.blockingFields())));
            return;
        }

        TaskRecord planTask = run("decompose-tasks", AgentRole.PLANNER, List.of(ambiguityTask.taskKey()), workflow, revision, actor, correlationId,
            task -> plannerAgent.execute(task, contextWithArtifacts(workflow, revision)));
        TaskPlan plan = read(store.findArtifact(revision.id(), "task-plan.json").orElseThrow().content(), TaskPlan.class);
        store.createArtifact(workflow.id(), revision.id(), planTask.id(), "engineering-plan.json", "application/json",
            write(Map.of("requirementHash", revision.requirementHash(), "taskPlan", plan)),
            write(Map.of("canonical", true, "sourceArtifact", "task-plan.json")));
        createPlannedTasks(workflow, revision, plan);
        run("analyze-repository", AgentRole.CODEBASE_ANALYST, List.of(planTask.taskKey()), workflow, revision, actor, correlationId,
            task -> repositoryAgent.execute(task, contextWithArtifacts(workflow, revision)));
        run("design-change", AgentRole.ARCHITECT, List.of("analyze-repository"), workflow, revision, actor, correlationId,
            task -> architectureAgent.execute(task, contextWithArtifacts(workflow, revision)));
        run("implement-change", AgentRole.IMPLEMENTER, List.of("design-change"), workflow, revision, actor, correlationId,
            task -> implementationAgent.execute(task, contextWithArtifacts(workflow, revision)));
        run("generate-tests", AgentRole.TEST_ENGINEER, List.of("design-change"), workflow, revision, actor, correlationId,
            task -> testGenerationAgent.execute(task, contextWithArtifacts(workflow, revision)));
        applyGeneratedPatch(workflow, revision, actor, correlationId);
        run("document-outcome", AgentRole.DOCUMENTATION_WRITER, List.of("apply-generated-patch"), workflow, revision, actor, correlationId,
            task -> documentationAgent.execute(task, contextWithArtifacts(workflow, revision)));
        run("security-risk-review", AgentRole.SECURITY_REVIEWER, List.of("design-change"), workflow, revision, actor, correlationId,
            task -> securityRiskReviewAgent.execute(task, contextWithArtifacts(workflow, revision)));
        run("release-readiness", AgentRole.RELEASE_REVIEWER, List.of("document-outcome", "security-risk-review"), workflow, revision, actor, correlationId,
            task -> releaseReadinessAgent.execute(task, contextWithArtifacts(workflow, revision)));
        store.createArtifact(workflow.id(), revision.id(), null, "engineering-outcome.json", "application/json",
            write(Map.of(
                "workflowId", workflow.id(),
                "revision", revision.revisionNumber(),
                "requirementHash", revision.requirementHash(),
                "planHash", store.findArtifact(revision.id(), "engineering-plan.json").orElseThrow().sha256(),
                "validationAttempts", store.listValidationAttempts(workflow.id()).size(),
                "releaseReadinessHash", store.findArtifact(revision.id(), "release-readiness.json").orElseThrow().sha256(),
                "status", "AWAITING_RELEASE_APPROVAL"
            )),
            write(Map.of("canonical", true, "requiredForGate", "release")));
        store.updateWorkflowStatus(workflow.id(), WorkflowStatus.AWAITING_RELEASE_APPROVAL);
        workflowMetrics.workflowCompleted("awaiting_release_approval");
        store.appendAuditEvent(workflow.id(), revision.id(), null, "workflow.awaiting-release-approval", actor, correlationId,
            "Agent execution completed and exact-evidence release approval is required.");
    }

    private TaskRecord applyGeneratedPatch(WorkflowRecord workflow, RevisionRecord revision, String actor, String correlationId) {
        TaskRecord task = existingTask(workflow, revision, "apply-generated-patch");
        if (task == null) {
            task = store.createTask(workflow.id(), revision.id(), "apply-generated-patch", "PATCH_APPLIER",
                write(List.of("implement-change", "generate-tests")));
        }
        store.updateTaskStatus(task.id(), TaskStatus.CLAIMED);
        store.incrementTaskAttempt(task.id());
        store.appendAuditEvent(workflow.id(), revision.id(), task.id(), "task.claimed", actor, correlationId,
            write(Map.of("taskKey", task.taskKey(), "agent", "PATCH_APPLIER")));
        store.updateTaskStatus(task.id(), TaskStatus.RUNNING);

        FileOperationProposalSet implementation = read(store.findArtifact(revision.id(), "implementation-proposal.json").orElseThrow().content(),
            FileOperationProposalSet.class);
        FileOperationProposalSet tests = read(store.findArtifact(revision.id(), "test-proposal.json").orElseThrow().content(),
            FileOperationProposalSet.class);
        PatchApplicationResult result = isolatedRepositoryService.apply(workflow.id().toString(), revision.revisionNumber(), List.of(implementation, tests));
        persistPatchEvidence(workflow, revision, task, result);
        if (result.policyDecision().allowed()) {
            store.updateTaskStatus(task.id(), TaskStatus.SUCCEEDED);
            workflowMetrics.taskCompleted(task.taskType(), "succeeded", 0);
            store.appendAuditEvent(workflow.id(), revision.id(), task.id(), "patch.applied", "PATCH_APPLIER", correlationId,
                write(Map.of("changedFiles", result.changedFiles(), "workspace", result.workspacePath())));
            validateWithBoundedRepair(workflow, revision, task, result, actor, correlationId);
        } else {
            store.updateTaskStatus(task.id(), TaskStatus.FAILED);
            store.updateWorkflowStatus(workflow.id(), WorkflowStatus.FAILED);
            workflowMetrics.taskCompleted(task.taskType(), "failed", 0);
            workflowMetrics.workflowCompleted("failed");
            store.appendAuditEvent(workflow.id(), revision.id(), task.id(), "patch.policy-rejected", "PATCH_APPLIER", correlationId,
                write(result.policyDecision()));
        }
        return store.findTask(task.id()).orElseThrow();
    }

    private void validateWithBoundedRepair(
        WorkflowRecord workflow,
        RevisionRecord revision,
        TaskRecord patchTask,
        PatchApplicationResult patchResult,
        String actor,
        String correlationId
    ) {
        TaskRecord validationTask = store.createTask(workflow.id(), revision.id(), "validate-generated-workspace", "VALIDATOR",
            write(List.of("apply-generated-patch")));
        store.updateTaskStatus(validationTask.id(), TaskStatus.RUNNING);
        store.incrementTaskAttempt(validationTask.id());
        BuildValidationEvidence firstAttempt = validationRunner.runCleanTest(Path.of(patchResult.workspacePath()), 1);
        persistValidationEvidence(workflow, revision, validationTask, firstAttempt);
        if (firstAttempt.successful()) {
            store.updateTaskStatus(validationTask.id(), TaskStatus.SUCCEEDED);
            workflowMetrics.taskCompleted(validationTask.taskType(), "succeeded", firstAttempt.durationMillis());
            store.appendAuditEvent(workflow.id(), revision.id(), validationTask.id(), "validation.passed", "VALIDATOR", correlationId, write(firstAttempt));
            return;
        }

        store.appendAuditEvent(workflow.id(), revision.id(), validationTask.id(), "validation.failed", "VALIDATOR", correlationId, write(firstAttempt));
        TaskRecord repairTask = runRepair(workflow, revision, patchResult, firstAttempt, actor, correlationId);
        BuildValidationEvidence secondAttempt = validationRunner.runCleanTest(Path.of(patchResult.workspacePath()), 2);
        persistValidationEvidence(workflow, revision, validationTask, secondAttempt);
        if (secondAttempt.successful()) {
            store.updateTaskStatus(validationTask.id(), TaskStatus.SUCCEEDED);
            workflowMetrics.taskCompleted(validationTask.taskType(), "succeeded", secondAttempt.durationMillis());
            store.appendAuditEvent(workflow.id(), revision.id(), validationTask.id(), "validation.revalidated", "VALIDATOR", correlationId, write(secondAttempt));
        } else {
            store.updateTaskStatus(validationTask.id(), TaskStatus.FAILED);
            store.updateWorkflowStatus(workflow.id(), WorkflowStatus.FAILED);
            workflowMetrics.taskCompleted(validationTask.taskType(), "failed", secondAttempt.durationMillis());
            workflowMetrics.rollback("required");
            workflowMetrics.workflowCompleted("failed");
            store.appendAuditEvent(workflow.id(), revision.id(), repairTask.id(), "rollback.required", "VALIDATOR", correlationId,
                "Repair budget exhausted; rollback will be implemented in the rollback checkpoint.");
        }
    }

    private TaskRecord runRepair(
        WorkflowRecord workflow,
        RevisionRecord revision,
        PatchApplicationResult patchResult,
        BuildValidationEvidence failedAttempt,
        String actor,
        String correlationId
    ) {
        String failedPath = "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSlice.java";
        TaskRecord task = store.createTask(workflow.id(), revision.id(), "repair-validation-failure", AgentRole.REPAIR_ENGINEER.name(),
            write(List.of("validate-generated-workspace")));
        store.updateTaskStatus(task.id(), TaskStatus.RUNNING);
        store.incrementTaskAttempt(task.id());
        String currentHash = fileHash(Path.of(patchResult.workspacePath()).resolve(failedPath));
        AgentTask agentTask = new AgentTask(task.taskKey(), AgentRole.REPAIR_ENGINEER, "Repair failed generated code", List.of("validate-generated-workspace"),
            Map.of(
                "failedPath", failedPath,
                "expectedCurrentSha256", currentHash,
                "failureClass", failedAttempt.failureClassification(),
                "stdout", failedAttempt.stdoutExcerpt(),
                "stderr", failedAttempt.stderrExcerpt()
            ));
        AgentExecutionResult<FileOperationProposalSet> repair = repairAgent.execute(agentTask,
            new ExecutionContext(workflow.id().toString(), revision.revisionNumber(), workflow.originalRequirement(), Map.of()));
        persistArtifacts(workflow, revision, task, repair);
        PatchApplicationResult repairResult = isolatedRepositoryService.applyToExisting(patchResult.workspacePath(), List.of(repair.output()));
        store.createArtifact(workflow.id(), revision.id(), task.id(), "repair-applied-file-operations.json", "application/json",
            write(Map.of("changedFiles", repairResult.changedFiles(), "workspacePath", repairResult.workspacePath())),
            write(Map.of("producingTask", task.taskKey(), "failedValidationAttempt", failedAttempt.attemptNumber())));
        store.createArtifact(workflow.id(), revision.id(), task.id(), "repair-diff.patch", "text/x-diff",
            repairResult.unifiedDiff(), write(Map.of("producingTask", task.taskKey())));
        store.updateTaskStatus(task.id(), repairResult.policyDecision().allowed() ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
        workflowMetrics.repairAttempt(repairResult.policyDecision().allowed() ? "proposal_applied" : "policy_rejected", 0);
        store.appendAuditEvent(workflow.id(), revision.id(), task.id(), "repair.applied", actor, correlationId,
            write(Map.of("allowed", repairResult.policyDecision().allowed(), "failureClass", failedAttempt.failureClassification())));
        return store.findTask(task.id()).orElseThrow();
    }

    private void persistValidationEvidence(WorkflowRecord workflow, RevisionRecord revision, TaskRecord task, BuildValidationEvidence evidence) {
        store.createValidationAttempt(workflow.id(), revision.id(), task.id(), evidence.attemptNumber(), evidence.commandName(), evidence.exitCode(),
            evidence.durationMillis(), evidence.timedOut(), evidence.failureClassification(), evidence.stdoutExcerpt(), evidence.stderrExcerpt());
        workflowMetrics.validationAttempt(evidence.successful() ? "succeeded" : "failed", evidence.failureClassification(), evidence.durationMillis());
        store.createArtifact(workflow.id(), revision.id(), task.id(), "validation-attempt-" + evidence.attemptNumber() + ".json", "application/json",
            write(evidence), write(Map.of("producingTask", task.taskKey())));
    }

    private String fileHash(Path path) {
        try {
            return com.assessment.agentic.persistence.Hashing.sha256(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash generated file for repair.", exception);
        }
    }

    private void persistPatchEvidence(WorkflowRecord workflow, RevisionRecord revision, TaskRecord task, PatchApplicationResult result) {
        store.createArtifact(workflow.id(), revision.id(), task.id(), "patch-policy.json", "application/json",
            write(result.policyDecision()), write(Map.of("producingTask", task.taskKey())));
        store.createArtifact(workflow.id(), revision.id(), task.id(), "applied-file-operations.json", "application/json",
            write(Map.of("changedFiles", result.changedFiles(), "workspacePath", result.workspacePath())), write(Map.of("producingTask", task.taskKey())));
        store.createArtifact(workflow.id(), revision.id(), task.id(), "unified-diff.patch", "text/x-diff",
            result.unifiedDiff(), write(Map.of("producingTask", task.taskKey())));
        store.createArtifact(workflow.id(), revision.id(), task.id(), "source-manifest.json", "application/json",
            result.policyDecision().allowed() ? isolatedRepositoryService.manifestJson(result.workspacePath()) : "[]",
            write(Map.of("baselineManifestHash", result.baselineManifestHash(), "appliedManifestHash", result.appliedManifestHash())));
    }

    private TaskRecord run(
        String taskKey,
        AgentRole role,
        List<String> dependencies,
        WorkflowRecord workflow,
        RevisionRecord revision,
        String actor,
        String correlationId,
        AgentInvoker invoker
    ) {
        TaskRecord taskRecord = existingTask(workflow, revision, taskKey);
        if (taskRecord == null) {
            taskRecord = store.createTask(workflow.id(), revision.id(), taskKey, role.name(), write(dependencies));
        }
        store.updateTaskStatus(taskRecord.id(), TaskStatus.CLAIMED);
        store.incrementTaskAttempt(taskRecord.id());
        store.appendAuditEvent(workflow.id(), revision.id(), taskRecord.id(), "task.claimed", actor, correlationId, write(Map.of("taskKey", taskKey, "agent", role)));
        store.updateTaskStatus(taskRecord.id(), TaskStatus.RUNNING);
        AgentExecutionResult<?> result = invoker.invoke(new AgentTask(taskKey, role, "Execute " + taskKey, dependencies, Map.of()));
        persistArtifacts(workflow, revision, taskRecord, result);
        store.updateTaskStatus(taskRecord.id(), TaskStatus.SUCCEEDED);
        workflowMetrics.taskCompleted(role.name(), "succeeded", 0);
        store.appendAuditEvent(workflow.id(), revision.id(), taskRecord.id(), "task.completed", role.name(), correlationId,
            write(Map.of("artifactCount", result.artifacts().size(), "schema", result.modelResult().schemaName())));
        return store.findTask(taskRecord.id()).orElseThrow();
    }

    private void createPlannedTasks(WorkflowRecord workflow, RevisionRecord revision, TaskPlan plan) {
        for (TaskPlan.PlannedTask plannedTask : plan.tasks()) {
            if (existingTask(workflow, revision, plannedTask.id()) == null) {
                store.createTask(workflow.id(), revision.id(), plannedTask.id(), plannedTask.agent().name(), write(plannedTask.dependencies()));
            }
        }
    }

    private TaskRecord existingTask(WorkflowRecord workflow, RevisionRecord revision, String taskKey) {
        return store.listTasks(workflow.id()).stream()
            .filter(task -> task.revisionId().equals(revision.id()))
            .filter(task -> task.taskKey().equals(taskKey))
            .findFirst()
            .orElse(null);
    }

    private void persistArtifacts(WorkflowRecord workflow, RevisionRecord revision, TaskRecord task, AgentExecutionResult<?> result) {
        for (AgentArtifact artifact : result.artifacts()) {
            store.createArtifact(workflow.id(), revision.id(), task.id(), artifact.name(), "application/json", render(result.output()),
                write(Map.of("producingAgent", artifact.producingAgent(), "producingTask", artifact.producingTaskId(), "model", result.modelResult().model())));
        }
    }

    private ExecutionContext contextWithArtifacts(WorkflowRecord workflow, RevisionRecord revision) {
        Map<String, String> artifacts = new LinkedHashMap<>();
        for (ArtifactRecord artifact : store.listArtifacts(workflow.id(), revision.id())) {
            artifacts.put(artifact.name(), artifact.sha256());
        }
        return new ExecutionContext(workflow.id().toString(), revision.revisionNumber(), workflow.originalRequirement(), artifacts);
    }

    private String render(Object value) {
        return write(value);
    }

    private <T> T read(String content, Class<T> type) {
        try {
            return objectMapper.readValue(content, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read workflow artifact.", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write workflow artifact.", exception);
        }
    }

    @FunctionalInterface
    private interface AgentInvoker {
        AgentExecutionResult<?> invoke(AgentTask task);
    }
}
