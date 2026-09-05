package com.assessment.agentic.api;

import com.assessment.agentic.orchestration.OrchestrationProperties;
import com.assessment.agentic.orchestration.WorkflowExecutionDispatcher;
import com.assessment.agentic.orchestration.WorkflowOrchestrator;
import com.assessment.agentic.persistence.ArtifactRecord;
import com.assessment.agentic.persistence.AuditEventRecord;
import com.assessment.agentic.persistence.ApprovalRecord;
import com.assessment.agentic.persistence.Hashing;
import com.assessment.agentic.persistence.IdempotencyRecord;
import com.assessment.agentic.persistence.RevisionRecord;
import com.assessment.agentic.persistence.TaskRecord;
import com.assessment.agentic.persistence.ValidationAttemptRecord;
import com.assessment.agentic.persistence.WorkflowRecord;
import com.assessment.agentic.persistence.WorkflowStateStore;
import com.assessment.agentic.persistence.WorkflowStatus;
import com.assessment.agentic.observability.WorkflowMetrics;
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
    private final WorkflowMetrics workflowMetrics;
    private final WorkflowExecutionDispatcher dispatcher;
    private final OrchestrationProperties orchestrationProperties;

    public WorkflowController(WorkflowStateStore store, WorkflowOrchestrator orchestrator, WorkflowMetrics workflowMetrics,
        WorkflowExecutionDispatcher dispatcher, OrchestrationProperties orchestrationProperties) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.workflowMetrics = workflowMetrics;
        this.dispatcher = dispatcher;
        this.orchestrationProperties = orchestrationProperties;
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
                    workflowMetrics.idempotencyReplay("conflict");
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used with a different request.");
                }
                WorkflowRecord replayed = store.findWorkflow(record.workflowId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency record points to a missing workflow."));
                workflowMetrics.idempotencyReplay("matched");
                return ResponseEntity.status(record.responseStatus()).body(WorkflowResponse.from(replayed));
            }
        }
        workflowMetrics.workflowSubmitted(request.scenarioKey());
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
        if (orchestrationProperties.isAsync()) {
            dispatcher.dispatchStartRevision(workflow, revision, principal.getName(), "api-submit-" + workflow.id());
        } else {
            orchestrator.startRevision(workflow, revision, principal.getName(), "api-submit-" + workflow.id());
        }
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
    ResponseEntity<ActionAcceptedResponse> clarify(
        @PathVariable("workflowId") UUID workflowId,
        @Valid @RequestBody ClarificationRequest request,
        Principal principal
    ) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        if (workflow.status() != WorkflowStatus.AWAITING_CLARIFICATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow is not awaiting clarification (current status " + workflow.status() + ").");
        }
        orchestrator.submitClarification(workflowId, request.questionId(), request.answer(),
            principal.getName(), "api-clarify-" + workflowId);
        WorkflowRecord updated = store.findWorkflow(workflowId).orElseThrow();
        return ResponseEntity.accepted().body(new ActionAcceptedResponse(
            "clarification accepted; workflow advanced to revision " + updated.currentRevision()
                + " with status " + updated.status() + "."));
    }

    @PostMapping("/api/workflows/{workflowId}/approvals/change")
    @PreAuthorize("hasRole('CHANGE_APPROVER')")
    ResponseEntity<ApprovalResponse> approveChange(
        @PathVariable("workflowId") UUID workflowId,
        @Valid @RequestBody ApprovalRequest request,
        Principal principal,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        boolean atChangeGate = workflow.status() == WorkflowStatus.AWAITING_CHANGE_APPROVAL;
        ApprovalRecord approval = recordApproval(workflow, "CHANGE", "ROLE_CHANGE_APPROVER", "engineering-plan.json", request, principal, servletRequest);
        if (atChangeGate) {
            if (orchestrationProperties.isAsync()) {
                dispatcher.dispatchResumeAfterChangeApproval(workflowId, principal.getName(), correlationId(servletRequest));
            } else {
                orchestrator.resumeAfterChangeApproval(workflowId, principal.getName(), correlationId(servletRequest));
            }
        }
        return ResponseEntity.accepted().body(ApprovalResponse.from(approval));
    }

    @PostMapping("/api/workflows/{workflowId}/approvals/release")
    @PreAuthorize("hasRole('RELEASE_APPROVER')")
    ResponseEntity<ApprovalResponse> approveRelease(
        @PathVariable("workflowId") UUID workflowId,
        @Valid @RequestBody ApprovalRequest request,
        Principal principal,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        ApprovalRecord approval = recordApproval(workflow, "RELEASE", "ROLE_RELEASE_APPROVER", "engineering-outcome.json", request, principal, servletRequest);
        store.updateWorkflowStatus(workflow.id(), WorkflowStatus.COMPLETED);
        workflowMetrics.workflowCompleted("completed");
        return ResponseEntity.accepted().body(ApprovalResponse.from(approval));
    }

    @PostMapping("/api/workflows/{workflowId}/safe-stop")
    @PreAuthorize("hasRole('OPERATOR')")
    ResponseEntity<ActionAcceptedResponse> safeStop(
        @PathVariable("workflowId") UUID workflowId,
        Principal principal,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        if (isTerminal(workflow.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Workflow is already in terminal state " + workflow.status() + ".");
        }
        orchestrator.safeStop(workflowId, principal.getName(), correlationId(servletRequest));
        WorkflowRecord updated = store.findWorkflow(workflowId).orElseThrow();
        return ResponseEntity.accepted().body(new ActionAcceptedResponse(
            "safe stop completed; workflow status " + updated.status() + "."));
    }

    private boolean isTerminal(WorkflowStatus status) {
        return status == WorkflowStatus.COMPLETED
            || status == WorkflowStatus.FAILED
            || status == WorkflowStatus.REJECTED
            || status == WorkflowStatus.CANCELLED
            || status == WorkflowStatus.SAFE_STOPPED;
    }

    @GetMapping("/api/workflows/{workflowId}/policies")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<PolicyResponse> policies(@PathVariable("workflowId") UUID workflowId) {
        WorkflowRecord workflow = requireWorkflow(workflowId);
        RevisionRecord revision = currentRevision(workflow);
        List<PolicyResponse> items = List.of(
            artifactPolicy(revision, "engineering-plan.json", "change-approval"),
            artifactPolicy(revision, "engineering-outcome.json", "release-approval")
        );
        return new PageResponse<>(items, 0, 50, items.size());
    }

    @GetMapping("/api/workflows/{workflowId}/approvals")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('CHANGE_APPROVER') or hasRole('RELEASE_APPROVER')")
    PageResponse<ApprovalResponse> approvals(@PathVariable("workflowId") UUID workflowId) {
        requireWorkflow(workflowId);
        List<ApprovalResponse> items = store.listApprovals(workflowId).stream()
            .map(ApprovalResponse::from)
            .toList();
        return new PageResponse<>(items, 0, 50, items.size());
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

    private ApprovalRecord recordApproval(
        WorkflowRecord workflow,
        String gate,
        String role,
        String artifactName,
        ApprovalRequest request,
        Principal principal,
        jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        RevisionRecord revision = currentRevision(workflow);
        ArtifactRecord artifact = store.findArtifact(revision.id(), artifactName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Required approval artifact is not available."));
        if (!artifact.sha256().equals(request.artifactHash())) {
            store.createApproval(workflow.id(), revision.id(), gate, principal.getName(), role, "REJECTED", request.reason(),
                artifactName, request.artifactHash(), artifact.sha256(), correlationId(servletRequest), false, "Supplied hash did not match current revision artifact.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplied artifact hash does not match current revision " + artifactName + ".");
        }
        ApprovalRecord approval = store.createApproval(workflow.id(), revision.id(), gate, principal.getName(), role, "APPROVED", request.reason(),
            artifactName, request.artifactHash(), artifact.sha256(), correlationId(servletRequest), true, null);
        store.appendAuditEvent(workflow.id(), revision.id(), null, "approval." + gate.toLowerCase() + ".approved", principal.getName(),
            correlationId(servletRequest), artifactName + "=" + artifact.sha256());
        return approval;
    }

    private PolicyResponse artifactPolicy(RevisionRecord revision, String artifactName, String gate) {
        return store.findArtifact(revision.id(), artifactName)
            .map(artifact -> new PolicyResponse(gate, "REQUIRES_HASH", artifactName, artifact.sha256()))
            .orElse(new PolicyResponse(gate, "MISSING_REQUIRED_ARTIFACT", artifactName, null));
    }

    private RevisionRecord currentRevision(WorkflowRecord workflow) {
        return store.findRevisionForWorkflowNumber(workflow.id(), workflow.currentRevision())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Revision not found."));
    }

    private String correlationId(jakarta.servlet.http.HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.HEADER);
        String header = request.getHeader(CorrelationIdFilter.HEADER);
        return header == null || header.isBlank() ? String.valueOf(value) : header;
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

    public record PolicyResponse(String policy, String decision, String requiredArtifactName, String currentArtifactHash) {
    }

    public record ApprovalResponse(
        String gate,
        String decision,
        String actor,
        String role,
        String requiredArtifactNames,
        String suppliedHashes,
        String canonicalReviewedEvidenceHash,
        boolean valid,
        String invalidationReason
    ) {
        static ApprovalResponse from(ApprovalRecord approval) {
            return new ApprovalResponse(approval.gate(), approval.decision(), approval.actor(), approval.role(), approval.requiredArtifactNames(),
                approval.suppliedHashes(), approval.canonicalReviewedEvidenceHash(), approval.valid(), approval.invalidationReason());
        }
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
