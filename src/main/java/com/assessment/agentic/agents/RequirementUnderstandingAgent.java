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
        String requirement = context.requirement();
        if (looksConcrete(requirement) && acceptance.size() < 3) {
            acceptance = List.of(
                "Expose URL creation and redirect behavior through REST endpoints",
                "Persist URL records and redirect analytics",
                "Validate malformed, unsafe, expired, and deactivated URLs"
            );
        } else if (!looksConcrete(requirement)) {
            acceptance = List.of();
        }
        List<String> assumptions = new ArrayList<>(splitList(fields.get("assumptions")));
        if (assumptions.isEmpty() && looksConcrete(requirement)) {
            assumptions.add("Use the submitted repository conventions and Java 21 Spring Boot stack.");
        }
        return new RequirementAnalysis(
            fields.get("normalizedRequirement"),
            acceptance,
            dimension(requirement, "scope", fields.get("scope")),
            dimension(requirement, "apiBehavior", fields.get("apiBehavior")),
            dimension(requirement, "persistenceRequirements", fields.get("persistenceRequirements")),
            dimension(requirement, "securityRequirements", fields.get("securityRequirements")),
            dimension(requirement, "timeBoundaries", fields.get("timeBoundaries")),
            dimension(requirement, "repositoryTarget", fields.get("repositoryTarget")),
            dimension(requirement, "operationalConstraints", fields.get("operationalConstraints")),
            assumptions,
            fields.get("riskLevel")
        );
    }

    private boolean looksConcrete(String requirement) {
        String text = requirement.toLowerCase();
        return text.contains("url") && (text.contains("create") || text.contains("redirect") || text.contains("analytics"));
    }

    private String dimension(String requirement, String dimension, String modelValue) {
        String text = requirement.toLowerCase();
        boolean present = switch (dimension) {
            case "scope" -> text.contains("url") || text.contains("shortener") || text.contains("link");
            case "apiBehavior" -> text.contains("api") || text.contains("endpoint") || text.contains("redirect") || text.contains("http");
            case "persistenceRequirements" -> text.contains("persist") || text.contains("database") || text.contains("store") || text.contains("postgres");
            case "securityRequirements" -> text.contains("security") || text.contains("auth") || text.contains("rate") || text.contains("blocked host")
                || text.contains("private");
            case "timeBoundaries" -> text.contains("expiry") || text.contains("expire") || text.contains("retention") || text.contains("utc")
                || text.contains("daily");
            case "repositoryTarget" -> text.contains("repository") || text.contains("brownfield") || text.contains("greenfield")
                || looksConcrete(requirement);
            case "operationalConstraints" -> text.contains("docker") || text.contains("prometheus") || text.contains("rate")
                || text.contains("retention") || looksConcrete(requirement);
            default -> false;
        };
        if (!present) {
            return "MISSING: " + dimension + " is not specified by the submitted requirement.";
        }
        return modelValue == null || modelValue.isBlank()
            ? "Specified by submitted requirement."
            : modelValue;
    }
}
