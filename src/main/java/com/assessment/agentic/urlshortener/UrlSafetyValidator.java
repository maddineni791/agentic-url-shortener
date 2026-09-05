package com.assessment.agentic.urlshortener;

import java.net.URI;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyValidator {

    private final UrlShortenerProperties properties;

    public UrlSafetyValidator(UrlShortenerProperties properties) {
        this.properties = properties;
    }

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
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (properties.getBlockedHosts().stream().map(blocked -> blocked.toLowerCase(Locale.ROOT)).anyMatch(host::equals)) {
            throw new UrlShortenerException("BLOCKED_HOST", "URL host is blocked by configuration.");
        }
        if (isLocalOrPrivate(host)) {
            throw new UrlShortenerException("PRIVATE_HOST_REJECTED", "Localhost and private network targets are not allowed.");
        }
        return uri;
    }

    private boolean isLocalOrPrivate(String host) {
        if (Set.of("localhost", "localhost.localdomain").contains(host) || host.endsWith(".localhost")) {
            return true;
        }
        if (host.equals("::1") || host.equals("[::1]")) {
            return true;
        }
        String value = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (value.startsWith("10.") || value.startsWith("127.") || value.startsWith("192.168.") || value.startsWith("169.254.")) {
            return true;
        }
        if (value.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return true;
        }
        return value.equals("0.0.0.0");
    }
}
