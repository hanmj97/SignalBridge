-- This extension/index is created ONLY in the separate signalbridge_perf database.
\set ON_ERROR_STOP on
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_bench_records_title_trgm
  ON bench_records USING GIN (title gin_trgm_ops);
ANALYZE bench_records;

SELECT indexname, indexdef FROM pg_indexes
WHERE tablename = 'bench_records' ORDER BY indexname;
