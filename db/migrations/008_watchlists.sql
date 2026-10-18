-- Phase 8. Idempotent migration for the EXISTING signalbridge database.
-- Execute once with psql -v ON_ERROR_STOP=1 (NOT in db/init).
BEGIN;
CREATE TABLE IF NOT EXISTS watch_rules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keyword TEXT NOT NULL CHECK (char_length(btrim(keyword)) BETWEEN 2 AND 80),
    source_id BIGINT REFERENCES sources(id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- PostgreSQL 17: prevent case-insensitive duplicate rules, including NULL source_ids.
CREATE UNIQUE INDEX IF NOT EXISTS uq_watch_rules_keyword_source
    ON watch_rules ((lower(btrim(keyword))), source_id) NULLS NOT DISTINCT;

CREATE TABLE IF NOT EXISTS watch_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES watch_rules(id) ON DELETE CASCADE,
    record_version_id BIGINT NOT NULL REFERENCES record_versions(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    error_code TEXT,
    UNIQUE (rule_id, record_version_id)
);
CREATE INDEX IF NOT EXISTS idx_watch_events_pending
    ON watch_events (status, id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_watch_events_rule_created
    ON watch_events (rule_id, created_at DESC);
COMMIT;
