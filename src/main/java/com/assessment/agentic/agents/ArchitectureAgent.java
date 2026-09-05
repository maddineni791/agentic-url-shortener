package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchitectureAgent extends BaseModelAgent<ArchitectureDecision> {

    public ArchitectureAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.ARCHITECT, ModelCapability.ARCHITECTURE,
            "architecture-v1", AgentSchemas.architecture(), "architecture.md", "ARCHITECTURE");
    }

    @Override
    protected ArchitectureDecision map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new ArchitectureDecision(fields.get("design"), fields.get("interfaces"), fields.get("tradeoffs"), fields.get("risks"));
    }
}
