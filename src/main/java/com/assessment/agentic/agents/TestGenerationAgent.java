package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TestGenerationAgent extends BaseModelAgent<FileOperationProposalSet> {

    public TestGenerationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.TEST_ENGINEER, ModelCapability.TEST_GENERATION,
            "file-operation-proposal-v1", AgentSchemas.proposal(), "test-proposal.json", "FILE_OPERATIONS");
    }

    @Override
    protected FileOperationProposalSet map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        String path = "src/test/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSliceTests.java";
        String expectedMode = "brownfield-analytics".equals(context.artifacts().get("scenarioKey"))
            ? "BROWNFIELD_ANALYTICS_ENHANCEMENT"
            : "GREENFIELD_VERTICAL_SLICE";
        FileOperationProposal operation = new FileOperationProposal(
            "CREATE",
            path,
            testContent(expectedMode),
            null,
            "Create behavior-focused tests for the generated URL-shortener production slice.",
            "REQ-005",
            task.id(),
            context.workflowId() + "/revision-" + context.revision() + "/" + task.id()
        );
        return new FileOperationProposalSet(List.of(operation), List.of(path), List.of(fields.get("lineage")));
    }

    private String testContent(String expectedMode) {
        return """
            package com.assessment.generated.urlshortener;

            import static org.assertj.core.api.Assertions.assertThat;
            import static org.assertj.core.api.Assertions.assertThatThrownBy;

            import java.net.URI;
            import java.time.Clock;
            import java.time.Instant;
            import java.time.ZoneOffset;
            import java.security.SecureRandom;
            import java.util.Map;
            import org.junit.jupiter.api.Test;

            class GeneratedUrlShortenerSliceTests {
                @Test
                void createsRedirectsAndReportsAnalytics() {
                    GeneratedUrlShortenerSlice service = new GeneratedUrlShortenerSlice(
                        Clock.fixed(Instant.parse("2026-09-05T10:15:30Z"), ZoneOffset.UTC),
                        new SecureRandom(new byte[] {1, 2, 3, 4}),
                        "us"
                    );

                    GeneratedShortUrl created = service.create("https://example.com/docs", Instant.parse("2026-09-06T00:00:00Z"));

                    assertThat(created.shortCode()).startsWith("us-");
                    assertThat(service.redirect(created.shortCode())).isEqualTo(URI.create("https://example.com/docs"));
                    assertThat(service.inspect(created.shortCode()).redirectCount()).isEqualTo(1);

                    Map<String, Object> analytics = service.analytics(created.shortCode());
                    assertThat(analytics).containsEntry("totalRedirects", 1L);
                    assertThat(analytics).containsEntry("generationMode", "%s");
                    assertThat(analytics.get("utcDailyRedirects").toString()).contains("2026-09-05");
                }

                @Test
                void rejectsUnsafeUrlsAndDeactivatedRedirects() {
                    GeneratedUrlShortenerSlice service = new GeneratedUrlShortenerSlice(
                        Clock.fixed(Instant.parse("2026-09-05T10:15:30Z"), ZoneOffset.UTC),
                        new SecureRandom(new byte[] {5, 6, 7, 8}),
                        "eu"
                    );

                    assertThatThrownBy(() -> service.create("ftp://example.com/file", Instant.parse("2026-09-06T00:00:00Z")))
                        .hasMessageContaining("HTTP and HTTPS");
                    assertThatThrownBy(() -> service.create("https://localhost/admin", Instant.parse("2026-09-06T00:00:00Z")))
                        .hasMessageContaining("blocked");
                    assertThatThrownBy(() -> service.create("https://user@example.com/private", Instant.parse("2026-09-06T00:00:00Z")))
                        .hasMessageContaining("user-info");

                    GeneratedShortUrl created = service.create("https://example.org/a", Instant.parse("2026-09-06T00:00:00Z"));
                    service.deactivate(created.shortCode());

                    assertThatThrownBy(() -> service.redirect(created.shortCode()))
                        .hasMessageContaining("deactivated");
                }
            }
            """.formatted(expectedMode);
    }
}
