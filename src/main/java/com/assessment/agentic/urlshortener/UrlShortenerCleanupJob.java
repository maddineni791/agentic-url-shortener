package com.assessment.agentic.urlshortener;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UrlShortenerCleanupJob {

    private final UrlShortenerService service;

    public UrlShortenerCleanupJob(UrlShortenerService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${agentic.url-shortener.cleanup-delay:PT1H}")
    void cleanupExpiredData() {
        service.cleanup();
    }
}
