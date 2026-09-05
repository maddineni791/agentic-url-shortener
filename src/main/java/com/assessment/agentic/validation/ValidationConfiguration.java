package com.assessment.agentic.validation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BuildValidationProperties.class)
public class ValidationConfiguration {
}
