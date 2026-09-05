create table workflows (
    id uuid primary key,
    external_id varchar(80) not null unique,
    scenario_key varchar(80) not null,
    status varchar(40) not null,
    original_requirement text not null,
    current_revision integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table workflow_revisions (
    id uuid primary key,
    workflow_id uuid not null references workflows(id) on delete cascade,
    revision_number integer not null,
    status varchar(40) not null,
    requirement_hash char(64) not null,
    parent_revision_id uuid references workflow_revisions(id),
    invalidated_at timestamp with time zone,
    invalidation_reason text,
    created_at timestamp with time zone not null,
    unique (workflow_id, revision_number)
);

create table workflow_tasks (
    id uuid primary key,
    workflow_id uuid not null references workflows(id) on delete cascade,
    revision_id uuid not null references workflow_revisions(id) on delete cascade,
    task_key varchar(120) not null,
    task_type varchar(80) not null,
    status varchar(40) not null,
    depends_on text not null default '[]',
    attempt_count integer not null default 0,
    max_attempts integer not null default 1,
    timeout_seconds integer not null default 300,
    lease_owner varchar(120),
    lease_expires_at timestamp with time zone,
    fencing_token bigint not null default 0,
    context_version integer not null default 1,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (revision_id, task_key)
);

create table workflow_artifacts (
    id uuid primary key,
    workflow_id uuid not null references workflows(id) on delete cascade,
    revision_id uuid not null references workflow_revisions(id) on delete cascade,
    producing_task_id uuid references workflow_tasks(id) on delete set null,
    name varchar(120) not null,
    media_type varchar(120) not null,
    sha256 char(64) not null,
    content text not null,
    lineage text not null default '{}',
    created_at timestamp with time zone not null,
    unique (revision_id, name)
);

create table validation_attempts (
    id uuid primary key,
    workflow_id uuid not null references workflows(id) on delete cascade,
    revision_id uuid not null references workflow_revisions(id) on delete cascade,
    task_id uuid references workflow_tasks(id) on delete set null,
    attempt_number integer not null,
    command_name varchar(80) not null,
    exit_code integer,
    duration_millis bigint not null,
    timed_out boolean not null,
    failure_classification varchar(80),
    stdout_excerpt text not null,
    stderr_excerpt text not null,
    created_at timestamp with time zone not null,
    unique (revision_id, command_name, attempt_number)
);

create table approvals (
    id uuid primary key,
    workflow_id uuid not null references workflows(id) on delete cascade,
    revision_id uuid not null references workflow_revisions(id) on delete cascade,
    gate varchar(80) not null,
    actor varchar(120) not null,
    role varchar(80) not null,
    decision varchar(40) not null,
    reason text,
    required_artifact_names text not null,
    supplied_hashes text not null,
    canonical_reviewed_evidence_hash char(64) not null,
    correlation_id varchar(120) not null,
    valid boolean not null,
    invalidation_reason text,
    decided_at timestamp with time zone not null
);

create table audit_events (
    id uuid primary key,
    workflow_id uuid references workflows(id) on delete cascade,
    revision_id uuid references workflow_revisions(id) on delete cascade,
    task_id uuid references workflow_tasks(id) on delete set null,
    event_type varchar(120) not null,
    actor varchar(120),
    correlation_id varchar(120) not null,
    redacted_payload text not null,
    original_payload_sha256 char(64) not null,
    created_at timestamp with time zone not null
);

create index idx_workflows_status on workflows(status);
create index idx_tasks_claimable on workflow_tasks(status, lease_expires_at);
create index idx_artifacts_revision on workflow_artifacts(revision_id);
create index idx_audit_workflow_created on audit_events(workflow_id, created_at desc);
