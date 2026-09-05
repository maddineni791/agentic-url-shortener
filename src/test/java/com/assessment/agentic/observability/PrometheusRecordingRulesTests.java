package com.assessment.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PrometheusRecordingRulesTests {

    @Test
    void recordingRulesDefineComputedReliabilityIndicators() throws Exception {
        String rules = Files.readString(Path.of("deploy/prometheus/agentic-recording-rules.yml"));

        assertThat(rules).contains("agentic:workflow_success_rate:5m");
        assertThat(rules).contains("agentic:retry_frequency_per_workflow:5m");
        assertThat(rules).contains("agentic:rollback_frequency_per_workflow:5m");
        assertThat(rules).contains("agentic:mean_time_to_repair_seconds:5m");
        assertThat(rules).contains("agentic:repair_success_rate:5m");
        assertThat(rules).contains("agentic:workflow_duration_p95_seconds:5m");
        assertThat(rules).contains("agentic:validation_failure_rate:5m");
        assertThat(rules).contains("agentic:model_failure_rate:5m");
        assertThat(rules).contains("histogram_quantile(0.95");
        assertThat(rules).doesNotContain("workflow_id");
        assertThat(rules).doesNotContain("repository_path");
        assertThat(rules).doesNotContain("user");
    }
}
