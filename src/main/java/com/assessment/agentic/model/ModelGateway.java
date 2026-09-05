package com.assessment.agentic.model;

import com.assessment.agentic.persistence.SecretRedactor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

public class ModelGateway {

    private final ModelProperties properties;
    private final ModelProvider provider;
    private final SecretRedactor secretRedactor;
    private final Validator validator;

    public ModelGateway(ModelProperties properties, ModelProvider provider, SecretRedactor secretRedactor, Validator validator) {
        this.properties = properties;
        this.provider = provider;
        this.secretRedactor = secretRedactor;
        this.validator = validator;
    }

    public ModelProviderType activeProviderType() {
        return provider.type();
    }

    public ModelResult invoke(ModelRequest request) {
        validate(request);
        String redactedPrompt = secretRedactor.redact(request.prompt());
        if (redactedPrompt.length() > properties.getMaxInputChars()) {
            throw new ModelContractException("Model input exceeds configured bound.");
        }
        ModelRequest boundedRequest = new ModelRequest(
            request.capability(),
            request.agentName(),
            request.schemaName(),
            redactedPrompt,
            request.requiredFields(),
            request.context()
        );
        ModelResult result = provider.invoke(boundedRequest, properties.getTimeout());
        validateResult(request, result);
        if (result.outputCharacters() > properties.getMaxOutputChars()) {
            throw new ModelContractException("Model output exceeds configured bound.");
        }
        return result;
    }

    private void validate(ModelRequest request) {
        Set<ConstraintViolation<ModelRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ModelContractException("Model request violates schema contract.");
        }
    }

    private void validateResult(ModelRequest request, ModelResult result) {
        if (!request.schemaName().equals(result.schemaName())) {
            throw new ModelContractException("Model result schema does not match request schema.");
        }
        for (String key : request.requiredFields().keySet()) {
            if (!result.fields().containsKey(key) || result.fields().get(key).isBlank()) {
                throw new ModelContractException("Model result missing required field: " + key);
            }
        }
    }
}
