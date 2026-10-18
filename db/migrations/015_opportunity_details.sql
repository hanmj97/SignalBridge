-- SignalBridge V2-1 | Non-destructive opportunity indexing layer
-- Requires db/init/001_schema.sql. No existing table/data is dropped or rewritten.
BEGIN;

CREATE TABLE IF NOT EXISTS opportunity_details (
    record_id BIGINT PRIMARY KEY REFERENCES records(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('POLICY','BENEFIT','JOB','PROGRAM')),
    organization TEXT,
    category TEXT,
    region_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    application_start DATE,
    application_deadline DATE,
    status TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (status IN ('OPEN','UPCOMING','CLOSED','UNKNOWN')),
    age_min SMALLINT CHECK (age_min BETWEEN 0 AND 120),
    age_max SMALLINT CHECK (age_max BETWEEN 0 AND 120),
    employment_type TEXT,
    eligibility_summary TEXT,
    benefit_summary TEXT,
    source_published_at TIMESTAMPTZ,
    is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_opportunity_dates CHECK (
        application_start IS NULL OR application_deadline IS NULL
        OR application_start <= application_deadline
    ),
    CONSTRAINT chk_opportunity_age CHECK (
        age_min IS NULL OR age_max IS NULL OR age_min <= age_max
    )
);

CREATE INDEX IF NOT EXISTS idx_opportunity_kind_deadline
    ON opportunity_details (kind, application_deadline);
CREATE INDEX IF NOT EXISTS idx_opportunity_status_deadline
    ON opportunity_details (status, application_deadline);
CREATE INDEX IF NOT EXISTS idx_opportunity_category
    ON opportunity_details (category);
CREATE INDEX IF NOT EXISTS idx_opportunity_region_gin
    ON opportunity_details USING GIN (region_codes);
CREATE INDEX IF NOT EXISTS idx_opportunity_demo
    ON opportunity_details (is_demo, kind);

COMMENT ON TABLE opportunity_details IS
    'V2 searchable details linked to records. Demo rows must never be represented as real policies or jobs.';
COMMENT ON COLUMN opportunity_details.status IS
    'Provider-derived status only; UNKNOWN when source does not explicitly state a status.';
COMMENT ON COLUMN opportunity_details.is_demo IS
    'TRUE denotes explicitly synthetic test data; public API must not mix demo records with real data.';

COMMIT;
