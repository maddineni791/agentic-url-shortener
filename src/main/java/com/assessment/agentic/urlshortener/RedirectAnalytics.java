package com.assessment.agentic.urlshortener;

import java.time.LocalDate;
import java.util.List;

public record RedirectAnalytics(
    String shortCode,
    long totalRedirects,
    List<DailyRedirectCount> dailyRedirects
) {
    public record DailyRedirectCount(LocalDate day, long redirects) {
    }
}
