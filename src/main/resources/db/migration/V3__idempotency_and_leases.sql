create table idempotency_keys (
    actor varchar(120) not null,
    idempotency_key varchar(160) not null,
    request_hash char(64) not null,
    workflow_id uuid not null references workflows(id) on delete cascade,
    response_status integer not null,
    created_at timestamp with time zone not null,
    primary key (actor, idempotency_key)
);

create index idx_idempotency_workflow on idempotency_keys(workflow_id);
