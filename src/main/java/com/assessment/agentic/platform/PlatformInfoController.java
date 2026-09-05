package com.assessment.agentic.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformInfoController {

    @GetMapping("/api/platform")
    PlatformInfo platformInfo() {
        return new PlatformInfo(
            "agentic-sdlc-platform",
            "checkpoint-1",
            "Agentic SDLC orchestration platform foundation"
        );
    }

    record PlatformInfo(String name, String checkpoint, String description) {
    }
}
