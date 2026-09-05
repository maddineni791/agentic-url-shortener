package com.assessment.agentic.urlshortener;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UrlShortenerRateLimitInterceptor implements HandlerInterceptor {

    private final UrlShortenerProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public UrlShortenerRateLimitInterceptor(UrlShortenerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getMethod().equals("POST") || !request.getRequestURI().equals("/api/urls")) {
            return true;
        }
        int limit = properties.getRateLimitPerMinute();
        if (limit <= 0) {
            return true;
        }
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(60);
        String key = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Deque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.removeFirst();
            }
            if (bucket.size() >= limit) {
                long retryAfter = Math.max(1, 60 - java.time.Duration.between(bucket.peekFirst(), now).toSeconds());
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                    {"type":"https://agentic-url-shortener.local/problems/rate-limited","title":"Rate limit exceeded","status":429,"detail":"Too many URL creation requests.","code":"RATE_LIMITED"}
                    """);
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }
}
