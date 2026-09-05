package com.assessment.agentic.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMetrics {

    private final MeterRegistry meterRegistry;

    public WorkflowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void workflowSubmitted(String scenarioKey) {
        meterRegistry.counter("agentic_workflows_submitted_total", "scenario", boundedScenario(scenarioKey)).increment();
    }

    public void workflowCompleted(String outcome) {
        meterRegistry.counter("agentic_workflows_terminal_total", "outcome", outcome).increment();
    }

    public void taskCompleted(String taskType, String outcome, long durationMillis) {
        meterRegistry.counter("agentic_tasks_total", "task_type", taskType, "outcome", outcome).increment();
        Timer.builder("agentic_task_duration")
            .tag("task_type", taskType)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(Duration.ofMillis(Math.max(0, durationMillis)));
    }

    public void validationAttempt(String outcome, String failureClass, long durationMillis) {
        meterRegistry.counter("agentic_validation_attempts_total", "outcome", outcome, "failure_class", failureClass).increment();
        Timer.builder("agentic_validation_duration")
            .tag("outcome", outcome)
            .tag("failure_class", failureClass)
            .register(meterRegistry)
            .record(Duration.ofMillis(Math.max(0, durationMillis)));
    }

    public void repairAttempt(String outcome, long durationMillis) {
        meterRegistry.counter("agentic_repair_attempts_total", "outcome", outcome).increment();
        Timer.builder("agentic_repair_duration")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(Duration.ofMillis(Math.max(0, durationMillis)));
    }

    public void rollback(String outcome) {
        meterRegistry.counter("agentic_rollbacks_total", "outcome", outcome).increment();
    }

    public void workflowRecovery(String outcome) {
        meterRegistry.counter("agentic_workflow_recoveries_total", "outcome", outcome).increment();
    }

    public void idempotencyReplay(String outcome) {
        meterRegistry.counter("agentic_idempotency_replays_total", "outcome", outcome).increment();
    }

    private String boundedScenario(String scenarioKey) {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            return "unknown";
        }
        return switch (scenarioKey) {
            case "greenfield-url-shortener", "brownfield-analytics", "ambiguous-requirement", "repair-demonstration" -> scenarioKey;
            default -> "other";
        };
    }
}
