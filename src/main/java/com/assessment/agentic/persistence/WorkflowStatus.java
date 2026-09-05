package com.assessment.agentic.persistence;

public enum WorkflowStatus {
    SUBMITTED,
    RUNNING,
    AWAITING_CLARIFICATION,
    AWAITING_CHANGE_APPROVAL,
    AWAITING_RELEASE_APPROVAL,
    COMPLETED,
    FAILED,
    REJECTED,
    CANCELLED,
    SAFE_STOPPED
}
