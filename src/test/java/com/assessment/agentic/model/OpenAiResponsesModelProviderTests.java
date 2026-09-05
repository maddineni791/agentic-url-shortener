package com.assessment.agentic.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class OpenAiResponsesModelProviderTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void callsResponsesApiAndParsesStructuredOutput() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = openAiStub(captured);
        server.start();
        ModelProperties.OpenAi properties = new ModelProperties.OpenAi();
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("test-model");
        OpenAiResponsesModelProvider provider = new OpenAiResponsesModelProvider(properties, RestClient.builder(), Clock.systemUTC());

        try {
            ModelResult result = provider.invoke(new ModelRequest(
                ModelCapability.ARCHITECTURE,
                "architecture-agent",
                "architecture-result",
                "Design it",
                Map.of("summary", "Summary", "status", "Status"),
                Map.of()
            ), Duration.ofSeconds(5));

            JsonNode body = objectMapper.readTree(captured.body);
            assertThat(captured.path).isEqualTo("/v1/responses");
            assertThat(captured.authorization).isEqualTo("Bearer test-key");
            assertThat(captured.contentType).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(body.path("model").asText()).isEqualTo("test-model");
            assertThat(result.provider()).isEqualTo(ModelProviderType.OPENAI);
            assertThat(result.model()).isEqualTo("test-model");
            assertThat(result.fields()).containsEntry("summary", "Generated plan");
            assertThat(result.inputTokens()).isEqualTo(12);
            assertThat(result.outputTokens()).isEqualTo(8);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requiresApiKeyOnlyWhenOpenAiProviderIsInvoked() {
        OpenAiResponsesModelProvider provider = new OpenAiResponsesModelProvider(
            new ModelProperties.OpenAi(),
            RestClient.builder(),
            Clock.systemUTC()
        );

        assertThatThrownBy(() -> provider.invoke(new ModelRequest(
            ModelCapability.REPAIR,
            "repair-agent",
            "repair-result",
            "Fix it",
            Map.of("summary", "Summary"),
            Map.of()
        ), Duration.ofSeconds(5))).isInstanceOf(ModelInvocationException.class)
            .hasMessageContaining("OPENAI_API_KEY");
    }

    private HttpServer openAiStub(CapturedRequest captured) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            captured.path = exchange.getRequestURI().getPath();
            captured.authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            captured.contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            captured.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = """
                {
                  "output_text": "{\\"summary\\":\\"Generated plan\\",\\"status\\":\\"READY\\"}",
                  "usage": {
                    "input_tokens": 12,
                    "output_tokens": 8
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        return server;
    }

    private static class CapturedRequest {
        private String path;
        private String authorization;
        private String contentType;
        private String body;
    }
}
