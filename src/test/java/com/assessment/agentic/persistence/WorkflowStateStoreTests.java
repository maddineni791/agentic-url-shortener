package com.assessment.agentic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowStateStoreTests {

    @Autowired
    private WorkflowStateStore store;

    @Test
    void createsWorkflowRevisionTaskArtifactAndAuditEvent() {
        WorkflowRecord workflow = store.createWorkflow("greenfield", "Build a URL shortener");
        RevisionRecord revision = store.createRevision(workflow.id(), 1, workflow.originalRequirement(), null);
        TaskRecord task = store.createTask(
            workflow.id(),
            revision.id(),
            "requirement-understanding",
            "REQUIREMENT_UNDERSTANDING",
            "[]"
        );
        ArtifactRecord artifact = store.createArtifact(
            workflow.id(),
            revision.id(),
            task.id(),
            "requirement-analysis",
            "application/json",
            "{\"summary\":\"Build a URL shortener\"}",
            "{\"inputs\":[\"original-requirement\"]}"
        );
        AuditEventRecord auditEvent = store.appendAuditEvent(
            workflow.id(),
            revision.id(),
            task.id(),
            "model.invocation",
            "deterministic-provider",
            "corr-1",
            "token=secret-value, result=ok"
        );

        assertThat(workflow.status()).isEqualTo(WorkflowStatus.SUBMITTED);
        assertThat(store.findWorkflow(workflow.id()).orElseThrow().currentRevision()).isEqualTo(1);
        assertThat(revision.revisionNumber()).isEqualTo(1);
        assertThat(revision.requirementHash()).hasSize(64);
        assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(task.dependsOnJson()).isEqualTo("[]");
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(store.findArtifact(revision.id(), "requirement-analysis")).contains(artifact);
        assertThat(auditEvent.redactedPayload()).contains("token=[REDACTED]");
        assertThat(auditEvent.redactedPayload()).doesNotContain("secret-value");
        assertThat(auditEvent.originalPayloadSha256()).hasSize(64);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-05T15:00:00Z"), ZoneOffset.UTC);
        }
    }
}
