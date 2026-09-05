package com.assessment.agentic.model;

import com.assessment.agentic.persistence.SecretRedactor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;

public class ModelGateway {

    private final ModelProperties properties;
    private final ModelProvider provider;
    private final SecretRedactor secretRedactor;
    private final Validator validator;
    private final MeterRegistry meterRegistry;

    public ModelGateway(ModelProperties properties, ModelProvider provider, SecretRedactor secretRedactor, Validator validator, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.provider = provider;
        this.secretRedactor = secretRedactor;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
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
        try {
            ModelResult result = provider.invoke(boundedRequest, properties.getTimeout());
            validateResult(request, result);
            if (result.outputCharacters() > properties.getMaxOutputChars()) {
                throw new ModelContractException("Model output exceeds configured bound.");
            }
            recordModelMetrics(request, result, "succeeded");
            return result;
        } catch (RuntimeException exception) {
            meterRegistry.counter("agentic_model_calls_total", "provider", provider.type().name(), "outcome", "failed").increment();
            throw exception;
        }
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

    private void recordModelMetrics(ModelRequest request, ModelResult result, String outcome) {
        meterRegistry.counter("agentic_model_calls_total", "provider", provider.type().name(), "outcome", outcome).increment();
        Timer.builder("agentic_model_latency")
            .tag("provider", provider.type().name())
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(result.latency() == null ? Duration.ZERO : result.latency());
        if (result.inputTokens() != null) {
            meterRegistry.counter("agentic_model_tokens_total", "provider", provider.type().name(), "direction", "input").increment(result.inputTokens());
        }
        if (result.outputTokens() != null) {
            meterRegistry.counter("agentic_model_tokens_total", "provider", provider.type().name(), "direction", "output").increment(result.outputTokens());
        }
    }
}
