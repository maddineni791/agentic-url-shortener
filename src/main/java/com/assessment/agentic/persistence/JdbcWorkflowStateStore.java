package com.assessment.agentic.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcWorkflowStateStore implements WorkflowStateStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final SecretRedactor secretRedactor;

    public JdbcWorkflowStateStore(JdbcTemplate jdbcTemplate, Clock clock, SecretRedactor secretRedactor) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.secretRedactor = secretRedactor;
    }

    @Override
    @Transactional
    public WorkflowRecord createWorkflow(String scenarioKey, String originalRequirement) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String externalId = "wf-" + id;
        jdbcTemplate.update(
            """
            insert into workflows (id, external_id, scenario_key, status, original_requirement, current_revision, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            externalId,
            scenarioKey,
            WorkflowStatus.SUBMITTED.name(),
            originalRequirement,
            0,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return findWorkflow(id).orElseThrow();
    }

    @Override
    @Transactional
    public RevisionRecord createRevision(UUID workflowId, int revisionNumber, String requirementText, UUID parentRevisionId) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into workflow_revisions
            (id, workflow_id, revision_number, status, requirement_hash, parent_revision_id, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            workflowId,
            revisionNumber,
            RevisionStatus.ACTIVE.name(),
            Hashing.sha256(requirementText),
            parentRevisionId,
            Timestamp.from(now)
        );
        jdbcTemplate.update(
            "update workflows set current_revision = ?, updated_at = ? where id = ?",
            revisionNumber,
            Timestamp.from(now),
            workflowId
        );
        return findRevision(id).orElseThrow();
    }

    @Override
    public TaskRecord createTask(UUID workflowId, UUID revisionId, String taskKey, String taskType, String dependsOnJson) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into workflow_tasks
            (id, workflow_id, revision_id, task_key, task_type, status, depends_on, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            workflowId,
            revisionId,
            taskKey,
            taskType,
            TaskStatus.PENDING.name(),
            dependsOnJson,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return findTask(id).orElseThrow();
    }

    @Override
    public ArtifactRecord createArtifact(
        UUID workflowId,
        UUID revisionId,
        UUID producingTaskId,
        String name,
        String mediaType,
        String content,
        String lineageJson
    ) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into workflow_artifacts
            (id, workflow_id, revision_id, producing_task_id, name, media_type, sha256, content, lineage, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            workflowId,
            revisionId,
            producingTaskId,
            name,
            mediaType,
            Hashing.sha256(content),
            content,
            lineageJson,
            Timestamp.from(now)
        );
        return findArtifact(revisionId, name).orElseThrow();
    }

    @Override
    public AuditEventRecord appendAuditEvent(
        UUID workflowId,
        UUID revisionId,
        UUID taskId,
        String eventType,
        String actor,
        String correlationId,
        String payload
    ) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update(
            """
            insert into audit_events
            (id, workflow_id, revision_id, task_id, event_type, actor, correlation_id, redacted_payload, original_payload_sha256, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            workflowId,
            revisionId,
            taskId,
            eventType,
            actor,
            correlationId,
            secretRedactor.redact(payload),
            Hashing.sha256(payload == null ? "" : payload),
            Timestamp.from(now)
        );
        return jdbcTemplate.queryForObject("select * from audit_events where id = ?", this::mapAuditEvent, id);
    }

    @Override
    public ValidationAttemptRecord createValidationAttempt(
        UUID workflowId,
        UUID revisionId,
        UUID taskId,
        int attemptNumber,
        String commandName,
        Integer exitCode,
        long durationMillis,
        boolean timedOut,
        String failureClassification,
        String stdoutExcerpt,
        String stderrExcerpt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into validation_attempts
            (id, workflow_id, revision_id, task_id, attempt_number, command_name, exit_code, duration_millis, timed_out,
             failure_classification, stdout_excerpt, stderr_excerpt, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            workflowId,
            revisionId,
            taskId,
            attemptNumber,
            commandName,
            exitCode,
            durationMillis,
            timedOut,
            failureClassification,
            stdoutExcerpt,
            stderrExcerpt,
            Timestamp.from(clock.instant())
        );
        return jdbcTemplate.queryForObject("select * from validation_attempts where id = ?", this::mapValidationAttempt, id);
    }

    @Override
    public Optional<WorkflowRecord> findWorkflow(UUID workflowId) {
        return jdbcTemplate.query("select * from workflows where id = ?", this::mapWorkflow, workflowId).stream().findFirst();
    }

    @Override
    public Optional<RevisionRecord> findRevision(UUID revisionId) {
        return jdbcTemplate.query("select * from workflow_revisions where id = ?", this::mapRevision, revisionId).stream().findFirst();
    }

    @Override
    public Optional<TaskRecord> findTask(UUID taskId) {
        return jdbcTemplate.query("select * from workflow_tasks where id = ?", this::mapTask, taskId).stream().findFirst();
    }

    @Override
    public Optional<ArtifactRecord> findArtifact(UUID revisionId, String name) {
        return jdbcTemplate.query(
            "select * from workflow_artifacts where revision_id = ? and name = ?",
            this::mapArtifact,
            revisionId,
            name
        ).stream().findFirst();
    }

    @Override
    public Optional<ArtifactRecord> findArtifactForWorkflowRevision(UUID workflowId, int revisionNumber, String name) {
        return jdbcTemplate.query(
            """
            select a.*
            from workflow_artifacts a
            join workflow_revisions r on r.id = a.revision_id
            where a.workflow_id = ? and r.revision_number = ? and a.name = ?
            """,
            this::mapArtifact,
            workflowId,
            revisionNumber,
            name
        ).stream().findFirst();
    }

    @Override
    public Optional<RevisionRecord> findRevisionForWorkflowNumber(UUID workflowId, int revisionNumber) {
        return jdbcTemplate.query(
            "select * from workflow_revisions where workflow_id = ? and revision_number = ?",
            this::mapRevision,
            workflowId,
            revisionNumber
        ).stream().findFirst();
    }

    @Override
    public List<TaskRecord> listTasks(UUID workflowId) {
        return jdbcTemplate.query(
            "select * from workflow_tasks where workflow_id = ? order by created_at, task_key",
            this::mapTask,
            workflowId
        );
    }

    @Override
    public List<ArtifactRecord> listArtifacts(UUID workflowId, UUID revisionId) {
        return jdbcTemplate.query(
            "select * from workflow_artifacts where workflow_id = ? and revision_id = ? order by created_at, name",
            this::mapArtifact,
            workflowId,
            revisionId
        );
    }

    @Override
    public List<AuditEventRecord> listAuditEvents(UUID workflowId) {
        return jdbcTemplate.query(
            "select * from audit_events where workflow_id = ? order by created_at",
            this::mapAuditEvent,
            workflowId
        );
    }

    @Override
    public List<ValidationAttemptRecord> listValidationAttempts(UUID workflowId) {
        return jdbcTemplate.query(
            "select * from validation_attempts where workflow_id = ? order by created_at, attempt_number",
            this::mapValidationAttempt,
            workflowId
        );
    }

    @Override
    public void updateWorkflowStatus(UUID workflowId, WorkflowStatus status) {
        jdbcTemplate.update(
            "update workflows set status = ?, updated_at = ? where id = ?",
            status.name(),
            Timestamp.from(clock.instant()),
            workflowId
        );
    }

    @Override
    public void updateTaskStatus(UUID taskId, TaskStatus status) {
        jdbcTemplate.update(
            "update workflow_tasks set status = ?, updated_at = ? where id = ?",
            status.name(),
            Timestamp.from(clock.instant()),
            taskId
        );
    }

    @Override
    public void incrementTaskAttempt(UUID taskId) {
        jdbcTemplate.update(
            "update workflow_tasks set attempt_count = attempt_count + 1, updated_at = ? where id = ?",
            Timestamp.from(clock.instant()),
            taskId
        );
    }

    private WorkflowRecord mapWorkflow(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowRecord(
            rs.getObject("id", UUID.class),
            rs.getString("external_id"),
            rs.getString("scenario_key"),
            WorkflowStatus.valueOf(rs.getString("status")),
            rs.getString("original_requirement"),
            rs.getInt("current_revision"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RevisionRecord mapRevision(ResultSet rs, int rowNum) throws SQLException {
        Timestamp invalidatedAt = rs.getTimestamp("invalidated_at");
        return new RevisionRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            rs.getInt("revision_number"),
            RevisionStatus.valueOf(rs.getString("status")),
            rs.getString("requirement_hash"),
            rs.getObject("parent_revision_id", UUID.class),
            invalidatedAt == null ? null : invalidatedAt.toInstant(),
            rs.getString("invalidation_reason"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private TaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
        Timestamp leaseExpiresAt = rs.getTimestamp("lease_expires_at");
        return new TaskRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            rs.getObject("revision_id", UUID.class),
            rs.getString("task_key"),
            rs.getString("task_type"),
            TaskStatus.valueOf(rs.getString("status")),
            rs.getString("depends_on"),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            rs.getInt("timeout_seconds"),
            rs.getString("lease_owner"),
            leaseExpiresAt == null ? null : leaseExpiresAt.toInstant(),
            rs.getLong("fencing_token"),
            rs.getInt("context_version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ArtifactRecord mapArtifact(ResultSet rs, int rowNum) throws SQLException {
        return new ArtifactRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            rs.getObject("revision_id", UUID.class),
            rs.getObject("producing_task_id", UUID.class),
            rs.getString("name"),
            rs.getString("media_type"),
            rs.getString("sha256"),
            rs.getString("content"),
            rs.getString("lineage"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private AuditEventRecord mapAuditEvent(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEventRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            rs.getObject("revision_id", UUID.class),
            rs.getObject("task_id", UUID.class),
            rs.getString("event_type"),
            rs.getString("actor"),
            rs.getString("correlation_id"),
            rs.getString("redacted_payload"),
            rs.getString("original_payload_sha256"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private ValidationAttemptRecord mapValidationAttempt(ResultSet rs, int rowNum) throws SQLException {
        return new ValidationAttemptRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("workflow_id", UUID.class),
            rs.getObject("revision_id", UUID.class),
            rs.getObject("task_id", UUID.class),
            rs.getInt("attempt_number"),
            rs.getString("command_name"),
            (Integer) rs.getObject("exit_code"),
            rs.getLong("duration_millis"),
            rs.getBoolean("timed_out"),
            rs.getString("failure_classification"),
            rs.getString("stdout_excerpt"),
            rs.getString("stderr_excerpt"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
