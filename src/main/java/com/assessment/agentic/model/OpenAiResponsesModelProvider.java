package com.assessment.agentic.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiResponsesModelProvider implements ModelProvider {

    private final ModelProperties.OpenAi properties;
    private final RestClient.Builder restClientBuilder;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiResponsesModelProvider(ModelProperties.OpenAi properties, RestClient.Builder restClientBuilder, Clock clock) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.clock = clock;
    }

    @Override
    public ModelProviderType type() {
        return ModelProviderType.OPENAI;
    }

    @Override
    public ModelResult invoke(ModelRequest request, Duration timeout) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ModelInvocationException("OPENAI_API_KEY is required when AGENTIC_MODEL_PROVIDER=OPENAI.");
        }
        Instant start = clock.instant();
        try {
            RestClient client = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .requestFactory(requestFactory(timeout))
                .build();
            Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", promptFor(request),
                "text", Map.of("format", Map.of("type", "json_object"))
            );
            String response = client.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            Map<String, String> fields = parseFields(response);
            return new ModelResult(
                ModelProviderType.OPENAI,
                properties.getModel(),
                request.schemaName(),
                fields,
                response == null ? "" : response,
                request.prompt().length(),
                response == null ? 0 : response.length(),
                Duration.between(start, clock.instant()),
                usage(response, "input_tokens"),
                usage(response, "output_tokens")
            );
        } catch (RestClientException ex) {
            throw new ModelInvocationException("OpenAI Responses API invocation failed.", ex);
        } catch (RuntimeException ex) {
            if (ex instanceof ModelInvocationException modelInvocationException) {
                throw modelInvocationException;
            }
            throw new ModelContractException("OpenAI response did not match the structured model contract.");
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private String promptFor(ModelRequest request) {
        return """
            Return only a JSON object matching schema '%s'.
            Required fields: %s
            Agent: %s
            Capability: %s
            Prompt:
            %s
            """.formatted(request.schemaName(), request.requiredFields(), request.agentName(), request.capability(), request.prompt());
    }

    private Map<String, String> parseFields(String response) {
        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String text = root.path("output_text").asText(null);
            if (text == null || text.isBlank()) {
                text = root.path("output").path(0).path("content").path(0).path("text").asText("{}");
            }
            JsonNode fieldsNode = objectMapper.readTree(text);
            Map<String, String> fields = new LinkedHashMap<>();
            fieldsNode.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue().asText()));
            return fields;
        } catch (Exception ex) {
            throw new ModelContractException("Unable to parse model response JSON.");
        }
    }

    private Integer usage(String response, String field) {
        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            JsonNode value = root.path("usage").path(field);
            return value.isInt() ? value.asInt() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
