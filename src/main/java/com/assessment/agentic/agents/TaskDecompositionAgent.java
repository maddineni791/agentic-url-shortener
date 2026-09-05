package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskDecompositionAgent extends BaseModelAgent<TaskPlan> {

    public TaskDecompositionAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.PLANNER, ModelCapability.TASK_DECOMPOSITION,
            "task-plan-v1", AgentSchemas.plan(), "task-plan.json", "TASK_PLAN");
    }

    @Override
    protected TaskPlan map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        List<TaskPlan.PlannedTask> tasks = List.of(
            new TaskPlan.PlannedTask("analyze-repository", AgentRole.CODEBASE_ANALYST, "Inspect bounded repository structure and conventions", List.of()),
            new TaskPlan.PlannedTask("design-change", AgentRole.ARCHITECT, "Design the requirement-specific URL shortener change", List.of("analyze-repository")),
            new TaskPlan.PlannedTask("implement-change", AgentRole.IMPLEMENTER, "Generate production file operations", List.of("design-change")),
            new TaskPlan.PlannedTask("generate-tests", AgentRole.TEST_ENGINEER, "Generate meaningful tests for the produced behavior", List.of("design-change")),
            new TaskPlan.PlannedTask("apply-generated-patch", AgentRole.PLANNER, "Evaluate policy and apply generated proposals in an isolated workspace", List.of("implement-change", "generate-tests")),
            new TaskPlan.PlannedTask("document-outcome", AgentRole.DOCUMENTATION_WRITER, "Generate reviewer documentation from applied evidence", List.of("apply-generated-patch")),
            new TaskPlan.PlannedTask("security-risk-review", AgentRole.SECURITY_REVIEWER, "Review generated artifacts and policy risk", List.of("design-change")),
            new TaskPlan.PlannedTask("release-readiness", AgentRole.RELEASE_REVIEWER, "Review validation, documentation, and risk evidence", List.of("document-outcome", "security-risk-review"))
        );
        return new TaskPlan(tasks, List.of("implement-change + generate-tests", "security-risk-review after design-change"),
            splitList(fields.get("gates")), fields.get("retryPolicy"));
    }
}
