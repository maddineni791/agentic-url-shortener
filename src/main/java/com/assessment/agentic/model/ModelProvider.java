package com.assessment.agentic.model;

import java.time.Duration;

public interface ModelProvider {

    ModelProviderType type();

    ModelResult invoke(ModelRequest request, Duration timeout);
}
