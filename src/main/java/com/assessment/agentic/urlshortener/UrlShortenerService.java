package com.assessment.agentic.urlshortener;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UrlShortenerService {

    private static final int MAX_COLLISION_ATTEMPTS = 8;

    private final JdbcShortUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlSafetyValidator safetyValidator;
    private final UrlShortenerProperties properties;
    private final Clock clock;

    public UrlShortenerService(
        JdbcShortUrlRepository repository,
        ShortCodeGenerator codeGenerator,
        UrlSafetyValidator safetyValidator,
        UrlShortenerProperties properties,
        Clock clock
    ) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.safetyValidator = safetyValidator;
        this.properties = properties;
        this.clock = clock;
    }

    public ShortUrlRecord create(String originalUrl, Instant expiresAt) {
        URI uri = safetyValidator.validate(originalUrl);
        Instant effectiveExpiry = expiresAt == null ? clock.instant().plus(properties.getDefaultTtl()) : expiresAt;
        if (!effectiveExpiry.isAfter(clock.instant())) {
            throw new UrlShortenerException("EXPIRY_MUST_BE_FUTURE", "Expiry must be in the future.");
        }
        for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
            try {
                return repository.create(codeGenerator.generate(properties.getCodeLength()), uri.toString(), effectiveExpiry);
            } catch (DuplicateKeyException ignored) {
                // Retry with a new secure random code.
            }
        }
        throw new UrlShortenerException("SHORT_CODE_COLLISION_EXHAUSTED", "Unable to allocate a unique short code.");
    }

    public URI redirectTarget(String code) {
        ShortUrlRecord record = inspect(code);
        if (!record.active()) {
            throw new UrlShortenerException("SHORT_URL_DEACTIVATED", "Short URL is deactivated.");
        }
        if (record.expired(clock.instant())) {
            throw new UrlShortenerException("SHORT_URL_EXPIRED", "Short URL is expired.");
        }
        repository.recordRedirect(record.id());
        return URI.create(record.originalUrl());
    }

    public ShortUrlRecord inspect(String code) {
        return repository.findByCode(code)
            .orElseThrow(() -> new UrlShortenerException("SHORT_CODE_NOT_FOUND", "Short code was not found."));
    }

    public ShortUrlRecord deactivate(String code) {
        repository.deactivate(code);
        return inspect(code);
    }
}
