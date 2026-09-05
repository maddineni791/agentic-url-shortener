package com.assessment.agentic.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class FixedMavenValidationRunner {

    private final BuildValidationProperties properties;
    private final Clock clock;

    public FixedMavenValidationRunner(BuildValidationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public BuildValidationEvidence runCleanTest(Path workspace, int attemptNumber) {
        Instant start = clock.instant();
        ProcessBuilder builder = new ProcessBuilder(mavenWrapperCommand(), "clean", "test");
        builder.directory(workspace.toFile());
        stripModelCredentials(builder.environment());
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String stdout = bounded(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String stderr = bounded(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            Integer exitCode = finished ? process.exitValue() : null;
            long duration = Duration.between(start, clock.instant()).toMillis();
            return new BuildValidationEvidence(attemptNumber, "maven-wrapper-clean-test", exitCode, duration, !finished,
                classify(!finished, exitCode, stdout, stderr), stdout, stderr);
        } catch (IOException exception) {
            long duration = Duration.between(start, clock.instant()).toMillis();
            return new BuildValidationEvidence(attemptNumber, "maven-wrapper-clean-test", null, duration, false,
                "INFRASTRUCTURE", "", bounded(exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            long duration = Duration.between(start, clock.instant()).toMillis();
            return new BuildValidationEvidence(attemptNumber, "maven-wrapper-clean-test", null, duration, true,
                "TIMEOUT", "", "Validation interrupted.");
        }
    }

    private String mavenWrapperCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return ".\\mvnw.cmd";
        }
        return "./mvnw";
    }

    private void stripModelCredentials(Map<String, String> environment) {
        environment.remove("OPENAI_API_KEY");
        environment.remove("AZURE_OPENAI_API_KEY");
        environment.remove("ANTHROPIC_API_KEY");
        environment.remove("AGENTIC_MODEL_PROVIDER");
    }

    private String classify(boolean timedOut, Integer exitCode, String stdout, String stderr) {
        if (timedOut) {
            return "TIMEOUT";
        }
        if (exitCode != null && exitCode == 0) {
            return "NONE";
        }
        String text = (stdout + "\n" + stderr).toLowerCase(Locale.ROOT);
        if (text.contains("compilation failure") || text.contains("compilation error") || text.contains("cannot find symbol")) {
            return "COMPILER";
        }
        if (text.contains("failures:") || text.contains("there are test failures")) {
            return "TEST";
        }
        if (text.contains("could not resolve") || text.contains("dependencyresolutionexception")) {
            return "DEPENDENCY";
        }
        if (text.contains("unable to obtain connection") || text.contains("configuration")) {
            return "CONFIGURATION";
        }
        return "INFRASTRUCTURE";
    }

    private String bounded(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= properties.getMaxOutputChars()) {
            return value;
        }
        return value.substring(0, properties.getMaxOutputChars()) + "\n...[truncated]";
    }
}
