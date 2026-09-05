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
        blocking.addAll(missingDimensions(context.requirement()));
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

    private List<String> missingDimensions(String requirement) {
        String text = requirement.toLowerCase();
        List<String> missing = new ArrayList<>();
        if (!text.contains("acceptance") && !text.contains("api") && !text.contains("endpoint") && !text.contains("redirect")) {
            missing.add("apiBehavior");
        }
        if (!text.contains("persist") && !text.contains("database") && !text.contains("store") && !text.contains("postgres")) {
            missing.add("persistenceRequirements");
        }
        if (!text.contains("security") && !text.contains("auth") && !text.contains("rate") && !text.contains("blocked host")) {
            missing.add("securityRequirements");
        }
        if (!text.contains("expiry") && !text.contains("retention") && !text.contains("utc")) {
            missing.add("timeBoundaries");
        }
        return missing;
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
