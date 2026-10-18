-- PHASE 5 - isolated performance database only (signalbridge_perf).
-- Data creation is intentionally synthetic. Production signalbridge tables are untouched.
\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS bench_sources (
    id BIGINT PRIMARY KEY,
    label TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS bench_records (
    id BIGINT PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES bench_sources(id),
    title TEXT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL
);

DROP INDEX IF EXISTS idx_bench_records_title_trgm;

-- Truncate both referencing/referenced tables in one statement (no CASCADE).
TRUNCATE TABLE bench_records, bench_sources;

INSERT INTO bench_sources(id, label) VALUES
  (1, 'Visual Studio Code'),
  (2, 'Docker Compose'),
  (3, 'Grafana');

INSERT INTO bench_records (id, source_id, title, first_seen_at)
SELECT
    n::BIGINT,
    ((n % 3) + 1)::BIGINT,
    CASE
        WHEN n % 2000 = 0 THEN 'RareRollbackAlert ' || n || ' release notice'
        WHEN n % 37 = 0 THEN 'Backend change ' || md5(n::TEXT)
        ELSE 'General release ' || md5(n::TEXT)
    END,
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + n * INTERVAL '5 seconds'
FROM generate_series(1, 150000) AS n;

CREATE INDEX IF NOT EXISTS idx_bench_records_source_seen ON bench_records(source_id, first_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_bench_records_first_seen ON bench_records(first_seen_at DESC);

ANALYZE bench_sources;
ANALYZE bench_records;

SELECT COUNT(*) AS benchmark_record_count,
       COUNT(*) FILTER (WHERE title ILIKE '%RareRollbackAlert%') AS expected_title_matches
FROM bench_records;
