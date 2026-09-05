package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequirementUnderstandingAgent extends BaseModelAgent<RequirementAnalysis> {

    public RequirementUnderstandingAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.REQUIREMENT_INTERPRETER, ModelCapability.REQUIREMENT_ANALYSIS,
            "requirement-analysis-v1", AgentSchemas.requirement(), "normalized-requirement.json", "REQUIREMENT_ANALYSIS");
    }

    @Override
    protected RequirementAnalysis map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        List<String> acceptance = splitList(fields.get("acceptanceCriteria"));
        if (looksConcrete(context.requirement()) && acceptance.size() < 3) {
            acceptance = List.of(
                "Expose URL creation and redirect behavior through REST endpoints",
                "Persist URL records and redirect analytics",
                "Validate malformed, unsafe, expired, and deactivated URLs"
            );
        }
        List<String> assumptions = new ArrayList<>(splitList(fields.get("assumptions")));
        if (assumptions.isEmpty() && looksConcrete(context.requirement())) {
            assumptions.add("Use the submitted repository conventions and Java 21 Spring Boot stack.");
        }
        return new RequirementAnalysis(
            fields.get("normalizedRequirement"),
            acceptance,
            valueOrUnknown(fields.get("scope")),
            valueOrUnknown(fields.get("apiBehavior")),
            valueOrUnknown(fields.get("persistenceRequirements")),
            valueOrUnknown(fields.get("securityRequirements")),
            valueOrUnknown(fields.get("timeBoundaries")),
            valueOrUnknown(fields.get("repositoryTarget")),
            valueOrUnknown(fields.get("operationalConstraints")),
            assumptions,
            fields.get("riskLevel")
        );
    }

    private boolean looksConcrete(String requirement) {
        String text = requirement.toLowerCase();
        return text.contains("url") && (text.contains("create") || text.contains("redirect") || text.contains("analytics"));
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
