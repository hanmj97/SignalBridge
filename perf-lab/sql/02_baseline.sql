-- Use BEFORE creating the GIN trigram index.
\set ON_ERROR_STOP on
\echo '=== BASELINE A: title-only substring search ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, r.first_seen_at
FROM bench_records r
WHERE r.title ILIKE '%RareRollbackAlert%'
ORDER BY r.first_seen_at DESC, r.id DESC
LIMIT 20;

\echo '=== BASELINE B: current API-style OR (title OR source label) ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r
JOIN bench_sources s ON s.id = r.source_id
WHERE r.title ILIKE '%RareRollbackAlert%' OR s.label ILIKE '%RareRollbackAlert%'
ORDER BY r.first_seen_at DESC, r.id DESC
LIMIT 20;
