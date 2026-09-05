package com.assessment.agentic.orchestration;

import com.assessment.agentic.persistence.RevisionRecord;
import com.assessment.agentic.persistence.WorkflowRecord;
import com.assessment.agentic.persistence.WorkflowStateStore;
import com.assessment.agentic.persistence.WorkflowStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs orchestration work off the request thread on the bounded {@code orchestrationExecutor}.
 * A failure inside an asynchronous run is captured here and recorded as a terminal
 * {@code FAILED} state with an audit event, since there is no caller to propagate it to.
 */
@Component
public class WorkflowExecutionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowExecutionDispatcher.class);

    private final WorkflowOrchestrator orchestrator;
    private final WorkflowStateStore store;

    public WorkflowExecutionDispatcher(WorkflowOrchestrator orchestrator, WorkflowStateStore store) {
        this.orchestrator = orchestrator;
        this.store = store;
    }

    @Async("orchestrationExecutor")
    public void dispatchStartRevision(WorkflowRecord workflow, RevisionRecord revision, String actor, String correlationId) {
        guard(workflow.id(), revision.id(), actor, correlationId,
            () -> orchestrator.startRevision(workflow, revision, actor, correlationId));
    }

    @Async("orchestrationExecutor")
    public void dispatchResumeAfterChangeApproval(UUID workflowId, String actor, String correlationId) {
        guard(workflowId, null, actor, correlationId,
            () -> orchestrator.resumeAfterChangeApproval(workflowId, actor, correlationId));
    }

    private void guard(UUID workflowId, UUID revisionId, String actor, String correlationId, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failure) {
            LOG.error("Asynchronous orchestration failed for workflow {}", workflowId, failure);
            try {
                store.updateWorkflowStatus(workflowId, WorkflowStatus.FAILED);
                store.appendAuditEvent(workflowId, revisionId, null, "workflow.execution-failed", actor, correlationId,
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
            } catch (RuntimeException recordFailure) {
                LOG.error("Unable to record asynchronous orchestration failure for workflow {}", workflowId, recordFailure);
            }
        }
    }
}
