package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AmbiguityClarificationAgent extends BaseModelAgent<AmbiguityAnalysis> {

    public AmbiguityClarificationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.AMBIGUITY_ANALYST, ModelCapability.AMBIGUITY_ANALYSIS,
            "ambiguity-analysis-v1", AgentSchemas.ambiguity(), "ambiguity-decision.json", "AMBIGUITY_ANALYSIS");
    }

    @Override
    protected AmbiguityAnalysis map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        List<String> blocking = new ArrayList<>(splitList(fields.get("blockingFields")).stream()
            .filter(this::isKnownRequirementDimension)
            .toList());
        blocking.addAll(missingDimensions(context.artifacts()));
        blocking = blocking.stream().distinct().toList();
        boolean requiresClarification = !blocking.isEmpty();
        List<String> questions = new ArrayList<>(splitList(fields.get("questions")));
        if (requiresClarification && questions.isEmpty()) {
            questions.add("What exact API behavior, persistence, security, and time-boundary acceptance criteria should govern this change?");
        }
        String reason = requiresClarification
            ? "Implementation is blocked because required engineering dimensions are missing or conflicting: " + String.join(", ", blocking)
            : fields.get("decisionReason");
        return new AmbiguityAnalysis(requiresClarification, questions, blocking, reason);
    }

    private List<String> missingDimensions(Map<String, String> artifacts) {
        List<String> missing = new ArrayList<>();
        if (isMissing("acceptanceCriteria", artifacts.get("requirement.acceptanceCriteria"))) {
            missing.add("acceptanceCriteria");
        }
        if (isMissing("scope", artifacts.get("requirement.scope"))) {
            missing.add("scope");
        }
        if (isMissing("apiBehavior", artifacts.get("requirement.apiBehavior"))) {
            missing.add("apiBehavior");
        }
        if (isMissing("persistenceRequirements", artifacts.get("requirement.persistenceRequirements"))) {
            missing.add("persistenceRequirements");
        }
        if (isMissing("securityRequirements", artifacts.get("requirement.securityRequirements"))) {
            missing.add("securityRequirements");
        }
        if (isMissing("timeBoundaries", artifacts.get("requirement.timeBoundaries"))) {
            missing.add("timeBoundaries");
        }
        if (isMissing("repositoryTarget", artifacts.get("requirement.repositoryTarget"))) {
            missing.add("repositoryTarget");
        }
        if (isMissing("operationalConstraints", artifacts.get("requirement.operationalConstraints"))) {
            missing.add("operationalConstraints");
        }
        return missing;
    }

    private boolean isMissing(String dimension, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase();
        return normalized.startsWith("missing:")
            || normalized.startsWith("conflicting:")
            || normalized.equals("unknown")
            || normalized.contains(dimension.toLowerCase() + " is not specified");
    }

    private boolean isKnownRequirementDimension(String value) {
        return value.equals("acceptanceCriteria")
            || value.equals("scope")
            || value.equals("apiBehavior")
            || value.equals("persistenceRequirements")
            || value.equals("securityRequirements")
            || value.equals("timeBoundaries")
            || value.equals("repositoryTarget")
            || value.equals("operationalConstraints");
    }
}
