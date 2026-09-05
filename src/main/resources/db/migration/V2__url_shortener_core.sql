create table short_urls (
    id uuid primary key,
    short_code varchar(32) not null unique,
    original_url text not null,
    active boolean not null,
    expires_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    redirect_count bigint not null default 0
);

create table redirect_events (
    id uuid primary key,
    short_url_id uuid not null references short_urls(id) on delete cascade,
    occurred_at timestamp with time zone not null
);

create index idx_short_urls_code on short_urls(short_code);
create index idx_redirect_events_short_url_time on redirect_events(short_url_id, occurred_at);
