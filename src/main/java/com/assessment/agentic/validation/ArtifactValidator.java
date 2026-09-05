package com.assessment.agentic.validation;

import com.assessment.agentic.agents.AgentArtifact;

public interface ArtifactValidator {

    String name();

    ValidationResult validate(AgentArtifact artifact, ValidationContext context);
}
