package com.assessment.agentic.repository;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RepositoryWorkspaceProperties.class)
public class RepositoryConfiguration {
}
