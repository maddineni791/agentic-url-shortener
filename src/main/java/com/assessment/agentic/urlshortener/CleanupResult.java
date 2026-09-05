package com.assessment.agentic.urlshortener;

public record CleanupResult(
    int redirectEventsDeleted,
    int shortUrlsDeleted
) {
}
