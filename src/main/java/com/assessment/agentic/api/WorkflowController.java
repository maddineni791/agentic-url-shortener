package com.assessment.agentic.api;

import com.assessment.agentic.orchestration.WorkflowOrchestrator;
import com.assessment.agentic.persistence.ArtifactRecord;
import com.assessment.agentic.persistence.AuditEventRecord;
import com.assessment.agentic.persistence.Hashing;
import com.assessment.agentic.persistence.IdempotencyRecord;
import com.assessment.agentic.persistence.RevisionRecord;
import com.assessment.agentic.persistence.TaskRecord;
import com.assessment.agentic.persistence.ValidationAttemptRecord;
import com.assessment.agentic.persistence.WorkflowRecord;
import com.assessment.agentic.persistence.WorkflowStateStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class WorkflowController {

    private final WorkflowStateStore store;
    private final WorkflowOrchestrator orchestrator;

    public WorkflowController(WorkflowStateStore store, WorkflowOrchestrator orchestrator) {
        this.store = store;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/api/workflows")
    @PreAuthorize("hasRole('OPERATOR')")
    ResponseEntity<WorkflowResponse> submitWorkflow(
        @Valid @RequestBody WorkflowSubmissionRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Principal principal
    ) {
        String requestHash = Hashing.sha256(request.scenarioKey() + "\n" + request.requirement() + "\n" + request.repositoryReference());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = store.findIdempotencyRecord(principal.getName(), idempotencyKey.trim());
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                if (!record.requestHash().equals(requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used with a different request.");
                }
                WorkflowRecord replayed = store.findWorkflow(record.workflowId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency record points to a missing workflow."));
                return ResponseEntity.status(record.responseStatus()).body(WorkflowResponse.from(replayed));
            }
        }
        WorkflowRecord workflow = store.createWorkflow(request.scenarioKey(), request.requirement());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            store.createIdempotencyRecord(principal.getName(), idempotencyKey.trim(), requestHash, workflow.id(), HttpStatus.CREATED.value());
        }
        var revision = store.createRevision(workflow.id(), 1, request.requirement(), null);
        store.appendAuditEvent(
            workflow.id(),
            revision.id(),
            null,
            "workflow.submitted",
            principal.getName(),
            "api-submit-" + workflow.id(),
            request.requirement()
        );
        orchestrator.startRevision(workflow, revision, principal.getName(), "api-submit-" + workflow.id());
        WorkflowRecord updated = store.findWorkflow(workflow.id()).orElseThrow();
        return ResponseEntity
            .created(URI.create("/api/workflows/" + workflow.id()))
            .body(WorkflowResponse.from(updated));
    }

    @GetMapping("/api/workflows/{workflowId}")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    WorkflowResponse workflow(@PathVariable("workflowId") UUID workflowId) {
        return store.findWorkflow(workflowId)
            .map(WorkflowResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found."));
    }

    @GetMapping("/api/workflows/{workflowId}/tasks")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<TaskStatusResponse> tasks(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        List<TaskStatusResponse> items = store.listTasks(workflowId).stream()
            .map(TaskStatusResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
    }

    @GetMapping("/api/workflows/{workflowId}/revisions")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<RevisionResponse> revisions(@PathVariable("workflowId") UUID workflowId) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        List<RevisionResponse> items = store.findRevisionForWorkflowNumber(workflow.id(), workflow.currentRevision()).stream()
            .map(RevisionResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
    }

    @GetMapping("/api/workflows/{workflowId}/artifacts")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<ArtifactSummaryResponse> artifacts(@PathVariable("workflowId") UUID workflowId) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        RevisionRecord revision = store.findRevisionForWorkflowNumber(workflow.id(), workflow.currentRevision())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revision not found."));
        List<ArtifactSummaryResponse> items = store.listArtifacts(workflowId, revision.id()).stream()
            .map(ArtifactSummaryResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
    }

    @GetMapping("/api/workflows/{workflowId}/artifacts/{name}")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    ArtifactContentResponse artifactContent(@PathVariable("workflowId") UUID workflowId, @PathVariable("name") String name) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        return store.findArtifactForWorkflowRevision(workflow.id(), workflow.currentRevision(), name)
            .map(ArtifactContentResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact not found."));
    }

    @PostMapping("/api/workflows/{workflowId}/clarifications")
    @PreAuthorize("hasRole('OPERATOR')")
    ResponseEntity<ActionAcceptedResponse> clarify(@PathVariable("workflowId") UUID workflowId, @Valid @RequestBody ClarificationRequest request) {
        requireWorkflow(workflowId);
        return ResponseEntity.accepted().body(new ActionAcceptedResponse("clarification accepted for later orchestration checkpoint"));
    }

    @PostMapping("/api/workflows/{workflowId}/approvals/change")
    @PreAuthorize("hasRole('CHANGE_APPROVER')")
    ResponseEntity<ActionAcceptedResponse> approveChange(@PathVariable("workflowId") UUID workflowId, @Valid @RequestBody ApprovalRequest request) {
        requireWorkflow(workflowId);
        return ResponseEntity.accepted().body(new ActionAcceptedResponse("change approval accepted for later governance checkpoint"));
    }

    @PostMapping("/api/workflows/{workflowId}/approvals/release")
    @PreAuthorize("hasRole('RELEASE_APPROVER')")
    ResponseEntity<ActionAcceptedResponse> approveRelease(@PathVariable("workflowId") UUID workflowId, @Valid @RequestBody ApprovalRequest request) {
        requireWorkflow(workflowId);
        return ResponseEntity.accepted().body(new ActionAcceptedResponse("release approval accepted for later governance checkpoint"));
    }

    @PostMapping("/api/workflows/{workflowId}/safe-stop")
    @PreAuthorize("hasRole('OPERATOR')")
    ResponseEntity<ActionAcceptedResponse> safeStop(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        return ResponseEntity.accepted().body(new ActionAcceptedResponse("safe stop accepted for later orchestration checkpoint"));
    }

    @GetMapping("/api/workflows/{workflowId}/policies")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<PolicyResponse> policies(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        return new PageResponse<>(List.of(), 0, 50, 0);
    }

    @GetMapping("/api/workflows/{workflowId}/approvals")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<ApprovalResponse> approvals(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        return new PageResponse<>(List.of(), 0, 50, 0);
    }

    @GetMapping("/api/workflows/{workflowId}/audit-events")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<AuditEventResponse> auditEvents(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        List<AuditEventResponse> items = store.listAuditEvents(workflowId).stream()
            .map(AuditEventResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
    }

    @GetMapping("/api/workflows/{workflowId}/validation-attempts")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<ValidationAttemptResponse> validationAttempts(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        List<ValidationAttemptResponse> items = store.listValidationAttempts(workflowId).stream()
            .map(ValidationAttemptResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
    }

    private WorkflowRecord requireWorkflow(UUID workflowId) {
        return store.findWorkflow(workflowId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found."));
    }

    public record WorkflowSubmissionRequest(
        @NotBlank @Size(max = 80) String scenarioKey,
        @NotBlank @Size(max = 20_000) String requirement,
        @Size(max = 500) String repositoryReference
    ) {
    }

    public record WorkflowResponse(UUID id, String externalId, String scenarioKey, String status, int currentRevision) {
        static WorkflowResponse from(WorkflowRecord workflow) {
            return new WorkflowResponse(
                workflow.id(),
                workflow.externalId(),
                workflow.scenarioKey(),
                workflow.status().name(),
                workflow.currentRevision()
            );
        }
    }

    public record TaskStatusResponse(
        String taskKey,
        String taskType,
        String status,
        int attemptCount,
        String dependsOn,
        String leaseOwner,
        String leaseExpiresAt,
        long fencingToken
    ) {
        static TaskStatusResponse from(TaskRecord task) {
            return new TaskStatusResponse(
                task.taskKey(),
                task.taskType(),
                task.status().name(),
                task.attemptCount(),
                task.dependsOnJson(),
                task.leaseOwner(),
                task.leaseExpiresAt() == null ? null : task.leaseExpiresAt().toString(),
                task.fencingToken()
            );
        }
    }

    public record RevisionResponse(int revisionNumber, String status, String requirementHash) {
        static RevisionResponse from(RevisionRecord revision) {
            return new RevisionResponse(revision.revisionNumber(), revision.status().name(), revision.requirementHash());
        }
    }

    public record ArtifactSummaryResponse(String name, String mediaType, String sha256, String producingTaskId) {
        static ArtifactSummaryResponse from(ArtifactRecord artifact) {
            return new ArtifactSummaryResponse(artifact.name(), artifact.mediaType(), artifact.sha256(),
                artifact.producingTaskId() == null ? null : artifact.producingTaskId().toString());
        }
    }

    public record ArtifactContentResponse(String name, String mediaType, String sha256, String content) {
        static ArtifactContentResponse from(ArtifactRecord artifact) {
            return new ArtifactContentResponse(artifact.name(), artifact.mediaType(), artifact.sha256(), artifact.content());
        }
    }

    public record ClarificationRequest(@NotBlank @Size(max = 120) String questionId, @NotBlank @Size(max = 5000) String answer) {
    }

    public record ApprovalRequest(@NotBlank @Size(min = 64, max = 64) String artifactHash, @Size(max = 1000) String reason) {
    }

    public record ActionAcceptedResponse(String message) {
    }

    public record PolicyResponse(String policy, String decision) {
    }

    public record ApprovalResponse(String gate, String decision) {
    }

    public record AuditEventResponse(String eventType, String correlationId, String actor, String payloadHash) {
        static AuditEventResponse from(AuditEventRecord event) {
            return new AuditEventResponse(event.eventType(), event.correlationId(), event.actor(), event.originalPayloadSha256());
        }
    }

    public record ValidationAttemptResponse(
        int attemptNumber,
        String commandName,
        Integer exitCode,
        long durationMillis,
        boolean timedOut,
        String failureClassification,
        String stdoutExcerpt,
        String stderrExcerpt
    ) {
        static ValidationAttemptResponse from(ValidationAttemptRecord attempt) {
            return new ValidationAttemptResponse(attempt.attemptNumber(), attempt.commandName(), attempt.exitCode(), attempt.durationMillis(),
                attempt.timedOut(), attempt.failureClassification(), attempt.stdoutExcerpt(), attempt.stderrExcerpt());
        }
    }
}
