package com.assessment.agentic.urlshortener;

import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyValidator {

    public URI validate(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new UrlShortenerException("MALFORMED_URL", "URL is malformed.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UrlShortenerException("UNSUPPORTED_URL_SCHEME", "Only HTTP and HTTPS URLs are supported.");
        }
        if (uri.getUserInfo() != null) {
            throw new UrlShortenerException("URL_USERINFO_REJECTED", "URL user-info is not allowed.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new UrlShortenerException("MALFORMED_URL", "URL host is required.");
        }
        return uri;
    }
}
