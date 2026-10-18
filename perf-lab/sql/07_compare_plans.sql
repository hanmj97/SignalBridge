-- Plans are gathered ONLY from signalbridge_perf and benchmark tables.
\set ON_ERROR_STOP on
\echo '=== RARE TITLE - CURRENT OR ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r JOIN bench_sources s ON s.id = r.source_id
WHERE r.title ILIKE '%RareRollbackAlert%' ESCAPE '!'
   OR s.label ILIKE '%RareRollbackAlert%' ESCAPE '!'
ORDER BY r.first_seen_at DESC, r.id DESC LIMIT 20 OFFSET 0;

\echo '=== RARE TITLE - UNION CANDIDATE ==='
EXPLAIN (ANALYZE, BUFFERS)
WITH matched_ids AS (
  SELECT r.id FROM bench_records r WHERE r.title ILIKE '%RareRollbackAlert%' ESCAPE '!'
  UNION
  SELECT r.id FROM bench_records r JOIN bench_sources s ON s.id=r.source_id
  WHERE s.label ILIKE '%RareRollbackAlert%' ESCAPE '!'
)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r JOIN bench_sources s ON s.id=r.source_id
JOIN matched_ids m ON m.id=r.id
ORDER BY r.first_seen_at DESC, r.id DESC LIMIT 20 OFFSET 0;

\echo '=== SOURCE LABEL - CURRENT OR ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r JOIN bench_sources s ON s.id = r.source_id
WHERE r.title ILIKE '%Grafana%' ESCAPE '!'
   OR s.label ILIKE '%Grafana%' ESCAPE '!'
ORDER BY r.first_seen_at DESC, r.id DESC LIMIT 20 OFFSET 0;

\echo '=== SOURCE LABEL - UNION CANDIDATE ==='
EXPLAIN (ANALYZE, BUFFERS)
WITH matched_ids AS (
  SELECT r.id FROM bench_records r WHERE r.title ILIKE '%Grafana%' ESCAPE '!'
  UNION
  SELECT r.id FROM bench_records r JOIN bench_sources s ON s.id=r.source_id
  WHERE s.label ILIKE '%Grafana%' ESCAPE '!'
)
SELECT r.id, r.title, s.label, r.first_seen_at
FROM bench_records r JOIN bench_sources s ON s.id=r.source_id
JOIN matched_ids m ON m.id=r.id
ORDER BY r.first_seen_at DESC, r.id DESC LIMIT 20 OFFSET 0;
