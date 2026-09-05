package com.assessment.agentic.validation;

import jakarta.validation.constraints.NotBlank;

public record ValidationResult(
    boolean valid,
    @NotBlank String validatorName,
    @NotBlank String evidence,
    String failureReason
) {
}
