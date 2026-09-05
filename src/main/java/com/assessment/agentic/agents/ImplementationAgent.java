package com.assessment.agentic.agents;

import com.assessment.agentic.model.ModelCapability;
import com.assessment.agentic.model.ModelGateway;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ImplementationAgent extends BaseModelAgent<FileOperationProposalSet> {

    public ImplementationAgent(ModelGateway modelGateway, Validator validator) {
        super(modelGateway, validator, AgentRole.IMPLEMENTER, ModelCapability.IMPLEMENTATION,
            "file-operation-proposal-v1", AgentSchemas.proposal(), "implementation-proposal.json", "FILE_OPERATIONS");
    }

    @Override
    protected FileOperationProposalSet map(AgentTask task, ExecutionContext context, Map<String, String> fields) {
        String scenarioKey = context.artifacts().getOrDefault("scenarioKey", "greenfield-url-shortener");
        boolean brownfield = "brownfield-analytics".equals(scenarioKey);
        boolean repairScenario = context.requirement().toLowerCase().contains("repair scenario");
        List<FileOperationProposal> operations = List.of(
            operation(context, task, "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerSlice.java",
                repairScenario ? brokenContent(brownfield) : sliceContent(brownfield),
                brownfield ? "Integrate redirect analytics enhancement with generated service facade." : "Create greenfield URL-shortener service facade."),
            operation(context, task, "src/main/java/com/assessment/generated/urlshortener/GeneratedShortUrl.java",
                recordContent(), "Create generated domain record for shortened URLs."),
            operation(context, task, "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerController.java",
                controllerContent(), "Create generated HTTP controller for create, redirect, inspect, deactivate, and analytics operations."),
            operation(context, task, "src/main/java/com/assessment/generated/urlshortener/GeneratedUrlShortenerRequest.java",
                requestContent(), "Create generated API request type with validation annotations.")
        );
        return new FileOperationProposalSet(operations, operations.stream().map(FileOperationProposal::normalizedRelativePath).toList(),
            List.of(fields.get("lineage"), brownfield ? "brownfield-analytics-enhancement" : "greenfield-vertical-slice"));
    }

    private FileOperationProposal operation(ExecutionContext context, AgentTask task, String path, String content, String reason) {
        return new FileOperationProposal(
            "CREATE",
            path,
            content,
            null,
            reason,
            "REQ-005",
            task.id(),
            context.workflowId() + "/revision-" + context.revision() + "/" + task.id()
        );
    }

    public static String workingContent() {
        return sliceContent(false);
    }

    private static String sliceContent(boolean brownfield) {
        String mode = brownfield ? "BROWNFIELD_ANALYTICS_ENHANCEMENT" : "GREENFIELD_VERTICAL_SLICE";
        return """
            package com.assessment.generated.urlshortener;

            import java.net.URI;
            import java.security.SecureRandom;
            import java.time.Clock;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.time.ZoneOffset;
            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicLong;

            public final class GeneratedUrlShortenerSlice {
                private static final char[] ALPHABET = "23456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

                private final Map<String, GeneratedShortUrl> urls = new ConcurrentHashMap<>();
                private final Map<String, List<Instant>> redirectEvents = new ConcurrentHashMap<>();
                private final SecureRandom random;
                private final Clock clock;
                private final AtomicLong collisionRetries = new AtomicLong();
                private final String regionPrefix;

                public GeneratedUrlShortenerSlice() {
                    this(Clock.systemUTC(), new SecureRandom(), "us");
                }

                GeneratedUrlShortenerSlice(Clock clock, SecureRandom random, String regionPrefix) {
                    this.clock = clock;
                    this.random = random;
                    this.regionPrefix = regionPrefix == null || regionPrefix.isBlank() ? "us" : regionPrefix;
                }

                public GeneratedShortUrl create(String targetUrl, Instant expiresAt) {
                    URI uri = validate(targetUrl);
                    Instant now = clock.instant();
                    Instant effectiveExpiry = expiresAt == null ? now.plusSeconds(86_400) : expiresAt;
                    if (!effectiveExpiry.isAfter(now)) {
                        throw new IllegalArgumentException("Expiry must be in the future.");
                    }
                    for (int attempt = 0; attempt < 20; attempt++) {
                        String code = regionPrefix + "-" + randomCode();
                        GeneratedShortUrl record = new GeneratedShortUrl(code, uri.toString(), true, effectiveExpiry, 0, now);
                        if (urls.putIfAbsent(code, record) == null) {
                            return record;
                        }
                        collisionRetries.incrementAndGet();
                    }
                    throw new IllegalStateException("Unable to allocate unique short code.");
                }

                public URI redirect(String code) {
                    GeneratedShortUrl record = inspect(code);
                    if (!record.active()) {
                        throw new IllegalStateException("Short URL is deactivated.");
                    }
                    if (record.expiresAt() != null && !record.expiresAt().isAfter(clock.instant())) {
                        throw new IllegalStateException("Short URL is expired.");
                    }
                    redirectEvents.computeIfAbsent(code, ignored -> new ArrayList<>()).add(clock.instant());
                    urls.computeIfPresent(code, (ignored, current) -> current.withRedirectCount(current.redirectCount() + 1));
                    return URI.create(record.targetUrl());
                }

                public GeneratedShortUrl inspect(String code) {
                    return Optional.ofNullable(urls.get(code))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown short code."));
                }

                public GeneratedShortUrl deactivate(String code) {
                    GeneratedShortUrl existing = inspect(code);
                    GeneratedShortUrl updated = existing.withActive(false);
                    urls.put(code, updated);
                    return updated;
                }

                public Map<String, Object> analytics(String code) {
                    GeneratedShortUrl current = inspect(code);
                    Map<LocalDate, Long> daily = new LinkedHashMap<>();
                    for (Instant instant : redirectEvents.getOrDefault(code, List.of())) {
                        LocalDate day = instant.atZone(ZoneOffset.UTC).toLocalDate();
                        daily.put(day, daily.getOrDefault(day, 0L) + 1L);
                    }
                    return Map.of(
                        "shortCode", code,
                        "totalRedirects", current.redirectCount(),
                        "utcDailyRedirects", daily,
                        "generationMode", "%s",
                        "collisionRetries", collisionRetries.get()
                    );
                }

                private URI validate(String targetUrl) {
                    if (targetUrl == null || targetUrl.isBlank()) {
                        throw new IllegalArgumentException("URL is required.");
                    }
                    URI uri = URI.create(targetUrl);
                    if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                        throw new IllegalArgumentException("Only HTTP and HTTPS URLs are supported.");
                    }
                    if (uri.getUserInfo() != null) {
                        throw new IllegalArgumentException("URL user-info is not allowed.");
                    }
                    String host = uri.getHost();
                    if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host) || host.startsWith("127.")) {
                        throw new IllegalArgumentException("Local and private hosts are blocked.");
                    }
                    return uri;
                }

                private String randomCode() {
                    StringBuilder code = new StringBuilder();
                    for (int i = 0; i < 8; i++) {
                        code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
                    }
                    return code.toString();
                }
            }
            """.formatted(mode);
    }

    private String brokenContent(boolean brownfield) {
        return sliceContent(brownfield).replace("return uri;", "return uri");
    }

    private String recordContent() {
        return """
            package com.assessment.generated.urlshortener;

            import java.time.Instant;

            public record GeneratedShortUrl(
                String shortCode,
                String targetUrl,
                boolean active,
                Instant expiresAt,
                long redirectCount,
                Instant createdAt
            ) {
                public GeneratedShortUrl withRedirectCount(long nextCount) {
                    return new GeneratedShortUrl(shortCode, targetUrl, active, expiresAt, nextCount, createdAt);
                }

                public GeneratedShortUrl withActive(boolean nextActive) {
                    return new GeneratedShortUrl(shortCode, targetUrl, nextActive, expiresAt, redirectCount, createdAt);
                }
            }
            """;
    }

    private String requestContent() {
        return """
            package com.assessment.generated.urlshortener;

            import jakarta.validation.constraints.NotBlank;
            import java.time.Instant;

            public record GeneratedUrlShortenerRequest(@NotBlank String url, Instant expiresAt) {
            }
            """;
    }

    private String controllerContent() {
        return """
            package com.assessment.generated.urlshortener;

            import jakarta.validation.Valid;
            import java.net.URI;
            import org.springframework.http.HttpHeaders;
            import org.springframework.http.HttpStatus;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PatchMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class GeneratedUrlShortenerController {
                private final GeneratedUrlShortenerSlice service = new GeneratedUrlShortenerSlice();

                @PostMapping("/generated/api/urls")
                GeneratedShortUrl create(@Valid @RequestBody GeneratedUrlShortenerRequest request) {
                    return service.create(request.url(), request.expiresAt());
                }

                @GetMapping("/generated/r/{code}")
                ResponseEntity<Void> redirect(@PathVariable String code) {
                    URI target = service.redirect(code);
                    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()).build();
                }

                @GetMapping("/generated/api/urls/{code}")
                GeneratedShortUrl inspect(@PathVariable String code) {
                    return service.inspect(code);
                }

                @GetMapping("/generated/api/urls/{code}/analytics")
                Object analytics(@PathVariable String code) {
                    return service.analytics(code);
                }

                @PatchMapping("/generated/api/urls/{code}/deactivate")
                GeneratedShortUrl deactivate(@PathVariable String code) {
                    return service.deactivate(code);
                }
            }
            """;
    }
}
