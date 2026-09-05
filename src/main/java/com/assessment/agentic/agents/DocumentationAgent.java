package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentationAgent extends BaseModelAgent<DocumentationPlan> {

    public DocumentationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.DOCUMENTATION_WRITER, ModelCapability.DOCUMENTATION,
            "documentation-plan-v1", AgentSchemas.documentation(), "documentation-plan.json", "DOCUMENTATION");
    }

    @Override
    protected DocumentationPlan map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new DocumentationPlan(splitList(fields.get("documents")), splitList(fields.get("reviewerSteps")), splitList(fields.get("limitations")));
    }
}
