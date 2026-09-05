package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ValidationDiagnosisAgent extends BaseModelAgent<ValidationDiagnosis> {

    public ValidationDiagnosisAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.VALIDATION_DIAGNOSTICIAN, ModelCapability.VALIDATION_DIAGNOSIS,
            "validation-diagnosis-v1", AgentSchemas.diagnosis(), "validation-diagnosis.json", "VALIDATION_DIAGNOSIS");
    }

    @Override
    protected ValidationDiagnosis map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        return new ValidationDiagnosis(fields.get("failureClass"), fields.get("rootCause"), fields.get("repairGuidance"));
    }
}
