-- SQLite: ids are UUID text, timestamps are UTC ISO-8601 text, booleans are 0/1.
create table app_user (
    id text primary key,
    phone text not null unique,
    role text not null check (role in ('WORKER','ADMIN','ASSESSOR')),
    can_publish integer not null default 0,
    can_decide integer not null default 0,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table audit_event (
    id text primary key,
    actor_id text,
    action text not null,
    entity_type text not null,
    entity_id text not null,
    detail text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table import_batch (
    id text primary key,
    source_type text not null,
    filename text not null,
    total_rows integer not null,
    versions_created integer not null,
    duplicates_skipped integer not null,
    qualifications_rejected integer not null,
    created_by text not null references app_user(id),
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table import_issue (
    id text primary key,
    batch_id text not null references import_batch(id),
    row_number integer,
    qualification_code text,
    severity text not null check (severity in ('ERROR','WARNING','INFO')),
    field text,
    message text not null
);
create index idx_import_issue_batch on import_issue(batch_id);

create table qualification (
    id text primary key,
    code text not null unique,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

create table qualification_version (
    id text primary key,
    qualification_id text not null references qualification(id),
    version integer not null,
    title text not null,
    trade text not null,
    nsqf_level integer check (nsqf_level between 1 and 10),
    source text not null,
    status text not null check (status in ('DRAFT','PUBLISHED','SUPERSEDED')),
    content_hash text not null,
    batch_id text references import_batch(id),
    published_by text references app_user(id),
    published_at text,
    created_at text not null default (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    unique (qualification_id, version),
    unique (qualification_id, content_hash)
);

create table competency (
    id text primary key,
    version_id text not null references qualification_version(id),
    position integer not null,
    code text not null,
    title text not null,
    mandatory integer not null,
    max_score integer not null check (max_score > 0),
    rubric text,
    unique (version_id, code)
);

create table evidence_requirement (
    id text primary key,
    competency_id text not null references competency(id),
    position integer not null,
    description text not null
);
create index idx_evidence_requirement_competency on evidence_requirement(competency_id);
