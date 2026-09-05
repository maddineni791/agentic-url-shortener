package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalysisAgent extends BaseModelAgent<RepositoryAnalysis> {

    public RepositoryAnalysisAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.CODEBASE_ANALYST, ModelCapability.REPOSITORY_ANALYSIS,
            "repository-analysis-v1", AgentSchemas.repository(), "repository-analysis.json", "REPOSITORY_ANALYSIS");
    }

    @Override
    protected RepositoryAnalysis map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new RepositoryAnalysis(fields.get("projectStructure"), fields.get("buildSystem"), fields.get("modules"),
            fields.get("apis"), fields.get("persistence"), fields.get("tests"), fields.get("changeImpact"));
    }
}
