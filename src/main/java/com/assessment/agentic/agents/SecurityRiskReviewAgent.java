package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SecurityRiskReviewAgent extends BaseModelAgent<SecurityRiskReview> {

    public SecurityRiskReviewAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.SECURITY_REVIEWER, ModelCapability.SECURITY_RISK_REVIEW,
            "security-risk-review-v1", AgentSchemas.risk(), "security-risk-review.json", "SECURITY_RISK_REVIEW");
    }

    @Override
    protected SecurityRiskReview map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new SecurityRiskReview(fields.get("riskLevel"), splitList(fields.get("findings")),
            splitList(fields.get("policyBlocks")), splitList(fields.get("mitigations")));
    }
}
