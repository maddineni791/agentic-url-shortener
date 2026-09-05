package com.assessment.agentic.urlshortener;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UrlShortenerProperties.class)
public class UrlShortenerConfiguration {
}
