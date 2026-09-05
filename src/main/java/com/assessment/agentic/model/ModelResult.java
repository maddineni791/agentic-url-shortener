package com.assessment.agentic.model;

import java.time.Duration;
import java.util.Map;

public record ModelResult(
    ModelProviderType provider,
    String model,
    String schemaName,
    Map<String, String> fields,
    String rawOutput,
    int inputCharacters,
    int outputCharacters,
    Duration latency,
    Integer inputTokens,
    Integer outputTokens
) {
}
