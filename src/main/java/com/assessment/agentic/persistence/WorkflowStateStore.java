package com.assessment.agentic.persistence;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowStateStore {

    WorkflowRecord createWorkflow(String scenarioKey, String originalRequirement);

    RevisionRecord createRevision(UUID workflowId, int revisionNumber, String requirementText, UUID parentRevisionId);

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

    Optional<WorkflowRecord> findWorkflow(UUID workflowId);

    Optional<RevisionRecord> findRevision(UUID revisionId);

    Optional<TaskRecord> findTask(UUID taskId);

    Optional<ArtifactRecord> findArtifact(UUID revisionId, String name);

    Optional<ArtifactRecord> findArtifactForWorkflowRevision(UUID workflowId, int revisionNumber, String name);
}
