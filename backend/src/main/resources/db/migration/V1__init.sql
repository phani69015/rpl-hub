create table app_user (
    id uuid primary key,
    phone varchar(20) not null unique,
    role varchar(20) not null check (role in ('WORKER','ADMIN','ASSESSOR')),
    can_publish boolean not null default false,
    created_at timestamptz not null default now()
);

create table audit_event (
    id uuid primary key,
    actor_id uuid,
    action varchar(60) not null,
    entity_type varchar(40) not null,
    entity_id varchar(64) not null,
    detail jsonb,
    created_at timestamptz not null default now()
);

create table import_batch (
    id uuid primary key,
    source_type varchar(20) not null,
    filename varchar(255) not null,
    total_rows int not null,
    versions_created int not null,
    duplicates_skipped int not null,
    qualifications_rejected int not null,
    created_by uuid not null references app_user(id),
    created_at timestamptz not null default now()
);

create table import_issue (
    id uuid primary key,
    batch_id uuid not null references import_batch(id),
    row_number int,
    qualification_code varchar(64),
    severity varchar(10) not null check (severity in ('ERROR','WARNING','INFO')),
    field varchar(40),
    message text not null
);
create index on import_issue(batch_id);

create table qualification (
    id uuid primary key,
    code varchar(64) not null unique,
    created_at timestamptz not null default now()
);

create table qualification_version (
    id uuid primary key,
    qualification_id uuid not null references qualification(id),
    version int not null,
    title varchar(255) not null,
    trade varchar(120) not null,
    nsqf_level int check (nsqf_level between 1 and 10),
    source varchar(255) not null,
    status varchar(12) not null check (status in ('DRAFT','PUBLISHED','SUPERSEDED')),
    content_hash char(64) not null,
    batch_id uuid references import_batch(id),
    published_by uuid references app_user(id),
    published_at timestamptz,
    created_at timestamptz not null default now(),
    unique (qualification_id, version),
    unique (qualification_id, content_hash)
);

create table competency (
    id uuid primary key,
    version_id uuid not null references qualification_version(id),
    position int not null,
    code varchar(64) not null,
    title varchar(255) not null,
    mandatory boolean not null,
    max_score int not null check (max_score > 0),
    rubric text,
    unique (version_id, code)
);

create table evidence_requirement (
    id uuid primary key,
    competency_id uuid not null references competency(id),
    position int not null,
    description text not null
);
create index on evidence_requirement(competency_id);
