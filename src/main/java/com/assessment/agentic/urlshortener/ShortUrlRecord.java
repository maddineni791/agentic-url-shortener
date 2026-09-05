package com.assessment.agentic.urlshortener;

import java.time.Instant;
import java.util.UUID;

public record ShortUrlRecord(
    UUID id,
    String shortCode,
    String originalUrl,
    boolean active,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    long redirectCount
) {
    boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
