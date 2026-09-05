package com.assessment.agentic.urlshortener;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
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
public class UrlShortenerController {

    private final UrlShortenerService service;

    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping("/api/urls")
    ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        ShortUrlRecord record = service.create(request.url(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShortUrlResponse.from(record));
    }

    @GetMapping("/r/{code}")
    ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        URI target = service.redirectTarget(code);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()).build();
    }

    @GetMapping("/api/urls/{code}")
    ShortUrlResponse inspect(@PathVariable("code") String code) {
        return ShortUrlResponse.from(service.inspect(code));
    }

    @GetMapping("/api/urls/{code}/analytics")
    RedirectAnalytics analytics(@PathVariable("code") String code) {
        return service.analytics(code);
    }

    @PatchMapping("/api/urls/{code}/deactivate")
    ShortUrlResponse deactivate(@PathVariable("code") String code) {
        return ShortUrlResponse.from(service.deactivate(code));
    }

    @PostMapping("/api/urls/cleanup")
    CleanupResult cleanup() {
        return service.cleanup();
    }

    public record CreateShortUrlRequest(
        @NotBlank @Size(max = 2048) String url,
        @Future Instant expiresAt
    ) {
    }

    public record ShortUrlResponse(
        String shortCode,
        String originalUrl,
        boolean active,
        Instant expiresAt,
        long redirectCount
    ) {
        static ShortUrlResponse from(ShortUrlRecord record) {
            return new ShortUrlResponse(record.shortCode(), record.originalUrl(), record.active(), record.expiresAt(), record.redirectCount());
        }
    }
}
