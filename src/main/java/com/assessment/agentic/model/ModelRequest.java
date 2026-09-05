package com.assessment.agentic.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ModelRequest(
    @NotNull ModelCapability capability,
    @NotBlank @Size(max = 120) String agentName,
    @NotBlank @Size(max = 120) String schemaName,
    @NotBlank @Size(max = 20_000) String prompt,
    @NotEmpty Map<@NotBlank String, @NotBlank String> requiredFields,
    Map<String, String> context
) {
}
