-- Re-run the same two queries after the trigram index is available.
\set ON_ERROR_STOP on
\echo '=== AFTER A: title-only substring search ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, r.first_seen_at
FROM bench_records r
WHERE r.title ILIKE '%RareRollbackAlert%'
ORDER BY r.first_seen_at DESC, r.id DESC
LIMIT 20;

\echo '=== AFTER B: current API-style OR (title OR source label) ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r
JOIN bench_sources s ON s.id = r.source_id
WHERE r.title ILIKE '%RareRollbackAlert%' OR s.label ILIKE '%RareRollbackAlert%'
ORDER BY r.first_seen_at DESC, r.id DESC
LIMIT 20;

\echo '=== AFTER C: equivalent UNION query (separate search branches) ==='
EXPLAIN (ANALYZE, BUFFERS)
WITH matched_ids AS (
    SELECT r.id FROM bench_records r
    WHERE r.title ILIKE '%RareRollbackAlert%'
    UNION
    SELECT r.id FROM bench_records r
    JOIN bench_sources s ON s.id = r.source_id
    WHERE s.label ILIKE '%RareRollbackAlert%'
)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r
JOIN matched_ids m ON m.id = r.id
JOIN bench_sources s ON s.id = r.source_id
ORDER BY r.first_seen_at DESC, r.id DESC
LIMIT 20;

\echo '=== LOGICAL RESULT VALIDATION: same IDs for OR and UNION ==='
WITH original_ids AS (
    SELECT r.id FROM bench_records r
    JOIN bench_sources s ON s.id = r.source_id
    WHERE r.title ILIKE '%RareRollbackAlert%' OR s.label ILIKE '%RareRollbackAlert%'
), union_ids AS (
    SELECT r.id FROM bench_records r WHERE r.title ILIKE '%RareRollbackAlert%'
    UNION
    SELECT r.id FROM bench_records r
    JOIN bench_sources s ON s.id = r.source_id
    WHERE s.label ILIKE '%RareRollbackAlert%'
)
SELECT
    (SELECT COUNT(*) FROM original_ids) AS original_count,
    (SELECT COUNT(*) FROM union_ids) AS rewritten_count,
    (SELECT COUNT(*) FROM (SELECT id FROM original_ids EXCEPT SELECT id FROM union_ids) mismatched) AS missing_in_union,
    (SELECT COUNT(*) FROM (SELECT id FROM union_ids EXCEPT SELECT id FROM original_ids) mismatched) AS extra_in_union;
