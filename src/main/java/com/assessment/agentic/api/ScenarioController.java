package com.assessment.agentic.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScenarioController {

    @GetMapping("/api/scenarios")
    PageResponse<ScenarioResponse> scenarios() {
        return new PageResponse<>(
            List.of(
                new ScenarioResponse("greenfield-url-shortener", "Generate a runnable URL shortener from a seed repository."),
                new ScenarioResponse("brownfield-analytics", "Enhance an existing URL shortener with reviewer-visible analytics."),
                new ScenarioResponse("ambiguous-requirement", "Pause mutation and request clarification for materially unclear requirements."),
                new ScenarioResponse("repair-demonstration", "Produce a real validation failure and repair it through the governed pipeline.")
            ),
            0,
            4,
            4
        );
    }

    record ScenarioResponse(String key, String description) {
    }
}
