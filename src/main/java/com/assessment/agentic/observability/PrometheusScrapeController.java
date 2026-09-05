package com.assessment.agentic.observability;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrometheusScrapeController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public PrometheusScrapeController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping(value = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    String scrape() {
        return prometheusMeterRegistry.scrape();
    }
}
