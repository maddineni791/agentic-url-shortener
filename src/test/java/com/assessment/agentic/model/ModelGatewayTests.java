package com.assessment.agentic.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assessment.agentic.persistence.SecretRedactor;
import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelGatewayTests {

    @Test
    void deterministicProviderReturnsStructuredFieldsWithoutApiKey() {
        ModelProperties properties = new ModelProperties();
        ModelGateway gateway = new ModelGateway(
            properties,
            new DeterministicModelProvider(java.time.Clock.systemUTC()),
            new SecretRedactor(),
            Validation.buildDefaultValidatorFactory().getValidator()
        );

        ModelResult result = gateway.invoke(new ModelRequest(
            ModelCapability.REQUIREMENT_ANALYSIS,
            "requirement-agent",
            "requirement-analysis",
            "Build a URL shortener",
            Map.of("summary", "Plain-language summary", "status", "Readiness"),
            Map.of()
        ));

        assertThat(result.provider()).isEqualTo(ModelProviderType.DETERMINISTIC);
        assertThat(result.schemaName()).isEqualTo("requirement-analysis");
        assertThat(result.fields()).containsKeys("summary", "status");
    }

    @Test
    void redactsSecretsBeforeInvokingProvider() {
        CapturingProvider provider = new CapturingProvider(Map.of("summary", "ok"));
        ModelGateway gateway = new ModelGateway(
            new ModelProperties(),
            provider,
            new SecretRedactor(),
            Validation.buildDefaultValidatorFactory().getValidator()
        );

        gateway.invoke(new ModelRequest(
            ModelCapability.SECURITY_RISK_REVIEW,
            "risk-agent",
            "risk-review",
            "token=super-secret",
            Map.of("summary", "Summary"),
            Map.of()
        ));

        assertThat(provider.prompt).contains("token=[REDACTED]");
        assertThat(provider.prompt).doesNotContain("super-secret");
    }

    @Test
    void rejectsMissingRequiredModelFields() {
        ModelGateway gateway = new ModelGateway(
            new ModelProperties(),
            new CapturingProvider(Map.of("other", "value")),
            new SecretRedactor(),
            Validation.buildDefaultValidatorFactory().getValidator()
        );

        assertThatThrownBy(() -> gateway.invoke(new ModelRequest(
            ModelCapability.TASK_DECOMPOSITION,
            "task-agent",
            "task-plan",
            "Split the work",
            Map.of("summary", "Summary"),
            Map.of()
        ))).isInstanceOf(ModelContractException.class)
            .hasMessageContaining("summary");
    }

    @Test
    void rejectsOversizedInputBeforeProviderCall() {
        ModelProperties properties = new ModelProperties();
        properties.setMaxInputChars(1000);
        ModelGateway gateway = new ModelGateway(
            properties,
            new CapturingProvider(Map.of("summary", "ok")),
            new SecretRedactor(),
            Validation.buildDefaultValidatorFactory().getValidator()
        );

        assertThatThrownBy(() -> gateway.invoke(new ModelRequest(
            ModelCapability.IMPLEMENTATION,
            "implementation-agent",
            "implementation-proposal",
            "x".repeat(1001),
            Map.of("summary", "Summary"),
            Map.of()
        ))).isInstanceOf(ModelContractException.class)
            .hasMessageContaining("input exceeds");
    }

    private static class CapturingProvider implements ModelProvider {
        private final Map<String, String> fields;
        private String prompt;

        CapturingProvider(Map<String, String> fields) {
            this.fields = fields;
        }

        @Override
        public ModelProviderType type() {
            return ModelProviderType.DETERMINISTIC;
        }

        @Override
        public ModelResult invoke(ModelRequest request, Duration timeout) {
            this.prompt = request.prompt();
            return new ModelResult(type(), "test-model", request.schemaName(), fields, fields.toString(), request.prompt().length(), 10, Duration.ZERO, null, null);
        }
    }
}
