package com.assessment.agentic.persistence;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SecretRedactor {

    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
        "(?i)(password|passwd|secret|token|api[_-]?key|authorization)(\\s*[:=]\\s*)([^\\s,;}]+)"
    );

    public String redact(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        return SECRET_ASSIGNMENT.matcher(payload).replaceAll("$1$2[REDACTED]");
    }
}
