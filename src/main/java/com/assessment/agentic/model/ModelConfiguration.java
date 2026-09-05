package com.assessment.agentic.model;

import com.assessment.agentic.persistence.SecretRedactor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelConfiguration {

    @Bean
    DeterministicModelProvider deterministicModelProvider(Clock clock) {
        return new DeterministicModelProvider(clock);
    }

    @Bean
    OpenAiResponsesModelProvider openAiResponsesModelProvider(ModelProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        return new OpenAiResponsesModelProvider(properties.getOpenai(), restClientBuilder, clock);
    }

    @Bean
    ModelGateway modelGateway(
        ModelProperties properties,
        DeterministicModelProvider deterministicProvider,
        OpenAiResponsesModelProvider openAiProvider,
        SecretRedactor secretRedactor,
        Validator validator,
        MeterRegistry meterRegistry
    ) {
        ModelProvider selected = properties.getProvider() == ModelProviderType.OPENAI ? openAiProvider : deterministicProvider;
        return new ModelGateway(properties, selected, secretRedactor, validator, meterRegistry);
    }
}
