package com.assessment.agentic.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WorkflowStateStore {

    WorkflowRecord createWorkflow(String scenarioKey, String originalRequirement);

    IdempotencyRecord createIdempotencyRecord(String actor, String idempotencyKey, String requestHash, UUID workflowId, int responseStatus);

    Optional<IdempotencyRecord> findIdempotencyRecord(String actor, String idempotencyKey);

    RevisionRecord createRevision(UUID workflowId, int revisionNumber, String requirementText, UUID parentRevisionId);

    void markRevisionStatus(UUID revisionId, RevisionStatus status, String reason);

    TaskRecord createTask(UUID workflowId, UUID revisionId, String taskKey, String taskType, String dependsOnJson);

    ArtifactRecord createArtifact(
        UUID workflowId,
        UUID revisionId,
        UUID producingTaskId,
        String name,
        String mediaType,
        String content,
        String lineageJson
    );

    AuditEventRecord appendAuditEvent(
        UUID workflowId,
        UUID revisionId,
        UUID taskId,
        String eventType,
        String actor,
        String correlationId,
        String payload
    );

    ValidationAttemptRecord createValidationAttempt(
        UUID workflowId,
        UUID revisionId,
        UUID taskId,
        int attemptNumber,
        String commandName,
        Integer exitCode,
        long durationMillis,
        boolean timedOut,
        String failureClassification,
        String stdoutExcerpt,
        String stderrExcerpt
    );

    ApprovalRecord createApproval(
        UUID workflowId,
        UUID revisionId,
        String gate,
        String actor,
        String role,
        String decision,
        String reason,
        String requiredArtifactNames,
        String suppliedHashes,
        String canonicalReviewedEvidenceHash,
        String correlationId,
        boolean valid,
        String invalidationReason
    );

    Optional<WorkflowRecord> findWorkflow(UUID workflowId);

    List<WorkflowRecord> listWorkflowsByStatus(WorkflowStatus status, int limit);

    Optional<RevisionRecord> findRevision(UUID revisionId);

    Optional<TaskRecord> findTask(UUID taskId);

    Optional<TaskRecord> claimTask(UUID taskId, String leaseOwner, int leaseSeconds);

    Optional<TaskRecord> heartbeatTaskLease(UUID taskId, String leaseOwner, long fencingToken, int leaseSeconds);

    boolean completeTaskWithFence(UUID taskId, String leaseOwner, long fencingToken, TaskStatus terminalStatus);

    Optional<ArtifactRecord> findArtifact(UUID revisionId, String name);

    Optional<ArtifactRecord> findArtifactForWorkflowRevision(UUID workflowId, int revisionNumber, String name);

    Optional<RevisionRecord> findRevisionForWorkflowNumber(UUID workflowId, int revisionNumber);

    List<TaskRecord> listTasks(UUID workflowId);

    List<ArtifactRecord> listArtifacts(UUID workflowId, UUID revisionId);

    List<AuditEventRecord> listAuditEvents(UUID workflowId);

    List<ValidationAttemptRecord> listValidationAttempts(UUID workflowId);

    List<ApprovalRecord> listApprovals(UUID workflowId);

    void updateWorkflowStatus(UUID workflowId, WorkflowStatus status);

    void updateTaskStatus(UUID taskId, TaskStatus status);

    void incrementTaskAttempt(UUID taskId);
}
