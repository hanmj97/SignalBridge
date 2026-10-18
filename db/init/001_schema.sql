-- PostgreSQL 17 / SignalBridge MVP schema
CREATE TABLE IF NOT EXISTS sources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_key TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL,
    provider TEXT NOT NULL,
    resource TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    etag TEXT,
    last_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id),
    external_id TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    payload JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_records_source_seen ON records (source_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_records_first_seen ON records (first_seen_at DESC);

CREATE TABLE IF NOT EXISTS record_versions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES records(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version >= 1),
    change_type TEXT NOT NULL CHECK (change_type IN ('NEW', 'UPDATED')),
    changed_fields TEXT[] NOT NULL DEFAULT '{}',
    content_hash CHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (record_id, version)
);
CREATE INDEX IF NOT EXISTS idx_record_versions_detected ON record_versions (detected_at DESC);

CREATE TABLE IF NOT EXISTS ingestion_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'NOT_MODIFIED')),
    fetched_count INTEGER NOT NULL DEFAULT 0,
    new_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);
CREATE INDEX IF NOT EXISTS idx_ingestion_runs_source_started ON ingestion_runs (source_id, started_at DESC);

INSERT INTO sources (source_key, label, provider, resource) VALUES
    ('github_vscode', 'Visual Studio Code', 'github_releases', 'microsoft/vscode'),
    ('github_docker_compose', 'Docker Compose', 'github_releases', 'docker/compose'),
    ('github_grafana', 'Grafana', 'github_releases', 'grafana/grafana')
ON CONFLICT (source_key) DO NOTHING;
