package com.assessment.agentic.validation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.validation")
public class BuildValidationProperties {

    private Duration timeout = Duration.ofSeconds(60);
    private int maxOutputChars = 12_000;

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    public void setMaxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }
}
