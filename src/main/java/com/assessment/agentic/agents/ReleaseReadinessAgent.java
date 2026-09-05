package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReleaseReadinessAgent extends BaseModelAgent<ReleaseReadiness> {

    public ReleaseReadinessAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.RELEASE_REVIEWER, ModelCapability.RELEASE_READINESS,
            "release-readiness-v1", AgentSchemas.release(), "release-readiness.json", "RELEASE_READINESS");
    }

    @Override
    protected ReleaseReadiness map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new ReleaseReadiness(Boolean.parseBoolean(fields.get("releaseReady")), splitList(fields.get("evidence")),
            splitList(fields.get("blockingIssues")), fields.get("outcome"));
    }
}
