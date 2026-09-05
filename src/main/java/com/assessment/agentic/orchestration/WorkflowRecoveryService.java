package com.assessment.agentic.orchestration;

import com.assessment.agentic.observability.WorkflowMetrics;
import com.assessment.agentic.persistence.ApprovalRecord;
import com.assessment.agentic.persistence.RevisionRecord;
import com.assessment.agentic.persistence.WorkflowRecord;
import com.assessment.agentic.persistence.WorkflowStateStore;
import com.assessment.agentic.persistence.WorkflowStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Re-drives revisions that an instance left in {@code RUNNING} because it stopped mid-flight.
 * Runs once on startup and then on {@code agentic.orchestration.recovery-interval}. Workflows
 * that are legitimately paused for a human ({@code AWAITING_*}) are never touched.
 */
@Service
public class WorkflowRecoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRecoveryService.class);

    private final WorkflowStateStore store;
    private final WorkflowOrchestrator orchestrator;
    private final WorkflowMetrics metrics;
    private final OrchestrationProperties properties;

    public WorkflowRecoveryService(WorkflowStateStore store, WorkflowOrchestrator orchestrator,
        WorkflowMetrics metrics, OrchestrationProperties properties) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.metrics = metrics;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (properties.isRecoveryScheduled()) {
            recoverInterruptedRevisions();
        }
    }

    @Scheduled(fixedDelayString = "${agentic.orchestration.recovery-interval:PT30S}")
    public void recoverOnSchedule() {
        if (properties.isRecoveryScheduled()) {
            recoverInterruptedRevisions();
        }
    }

    /** Scans for {@code RUNNING} workflows and re-drives each from its last durable checkpoint. */
    public int recoverInterruptedRevisions() {
        List<WorkflowRecord> interrupted = store.listWorkflowsByStatus(WorkflowStatus.RUNNING, properties.getRecoveryBatchSize());
        int recovered = 0;
        for (WorkflowRecord workflow : interrupted) {
            try {
                recover(workflow);
                recovered++;
            } catch (RuntimeException failure) {
                metrics.workflowRecovery("failed");
                LOG.error("Recovery failed for workflow {}", workflow.id(), failure);
                store.updateWorkflowStatus(workflow.id(), WorkflowStatus.FAILED);
                store.appendAuditEvent(workflow.id(), null, null, "workflow.recovery-failed", "RECOVERY", "recovery-" + workflow.id(),
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            }
        }
        return recovered;
    }

    private void recover(WorkflowRecord workflow) {
        RevisionRecord revision = store.findRevisionForWorkflowNumber(workflow.id(), workflow.currentRevision())
            .orElseThrow(() -> new IllegalStateException("Interrupted workflow has no active revision."));
        String correlationId = "recovery-" + workflow.id();

        if (store.findArtifact(revision.id(), "engineering-outcome.json").isPresent()) {
            store.updateWorkflowStatus(workflow.id(), WorkflowStatus.AWAITING_RELEASE_APPROVAL);
            audit(workflow, revision, "engineering-outcome present; awaiting release approval");
            metrics.workflowRecovery("resumed_release_gate");
            return;
        }
        if (hasValidChangeApproval(workflow, revision)) {
            audit(workflow, revision, "valid change approval present; re-running build phase");
            metrics.workflowRecovery("resumed_build_phase");
            orchestrator.resumeAfterChangeApproval(workflow.id(), "RECOVERY", correlationId);
            return;
        }
        if (store.findArtifact(revision.id(), "engineering-plan.json").isPresent()) {
            store.updateWorkflowStatus(workflow.id(), WorkflowStatus.AWAITING_CHANGE_APPROVAL);
            audit(workflow, revision, "engineering-plan present; re-pausing at change gate");
            metrics.workflowRecovery("resumed_change_gate");
            return;
        }
        audit(workflow, revision, "no durable design artifacts; restarting revision from requirement analysis");
        metrics.workflowRecovery("restarted_revision");
        orchestrator.startRevision(store.findWorkflow(workflow.id()).orElseThrow(), revision, "RECOVERY", correlationId);
    }

    private boolean hasValidChangeApproval(WorkflowRecord workflow, RevisionRecord revision) {
        return store.listApprovals(workflow.id()).stream()
            .filter(ApprovalRecord::valid)
            .filter(approval -> "CHANGE".equals(approval.gate()))
            .filter(approval -> "APPROVED".equals(approval.decision()))
            .anyMatch(approval -> revision.id().equals(approval.revisionId()));
    }

    private void audit(WorkflowRecord workflow, RevisionRecord revision, String detail) {
        store.appendAuditEvent(workflow.id(), revision.id(), null, "workflow.recovery-resumed", "RECOVERY",
            "recovery-" + workflow.id(), detail);
        LOG.info("Recovered workflow {} revision {}: {}", workflow.id(), revision.revisionNumber(), detail);
    }
}
