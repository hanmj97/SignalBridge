-- SignalBridge Phase 5.5: search performance / logical parity comparison.
-- Runs in signalbridge_perf ONLY. Creates TEMP tables, no permanent schema changes.
-- Requirements: Phase 5 data (bench_records and bench_sources) and trigram index.
\set ON_ERROR_STOP on
\echo '=== Phase 5.5: compare existing OR search vs UNION candidate ==='

SET jit = off;

CREATE TEMP TABLE sb55_workloads (
  case_name TEXT PRIMARY KEY,
  search_text TEXT NOT NULL,
  source_id BIGINT,
  page_number INTEGER NOT NULL,
  page_size INTEGER NOT NULL
);

-- Cover highly selective, common, source-only, source filter, no-match,
-- case-insensitive and literal wildcard input scenarios.
INSERT INTO sb55_workloads (case_name, search_text, source_id, page_number, page_size) VALUES
  ('rare_title',        'RareRollbackAlert', NULL, 0, 20),
  ('medium_title',      'Backend change',    NULL, 1, 20),
  ('common_title',      'General release',   NULL, 4, 20),
  ('source_label',      'Grafana',           NULL, 0, 20),
  ('filtered_label',    'Grafana',              3, 1, 20),
  ('no_match',          'not-found-zxq913', NULL, 0, 20),
  ('literal_wildcard',  '%_!',              NULL, 0, 20),
  ('mixed_case_source', 'gRaFaNa',           NULL, 2, 20);

CREATE TEMP TABLE sb55_parity (
  case_name TEXT PRIMARY KEY,
  original_count BIGINT NOT NULL,
  candidate_count BIGINT NOT NULL,
  missing_ids BIGINT NOT NULL,
  extra_ids BIGINT NOT NULL,
  page_identical BOOLEAN NOT NULL
);

CREATE TEMP TABLE sb55_samples (
  case_name TEXT NOT NULL,
  strategy TEXT NOT NULL CHECK (strategy IN ('OR', 'UNION')),
  iteration INTEGER NOT NULL,
  time_ms NUMERIC(12,3) NOT NULL,
  returned_count INTEGER NOT NULL,
  total_count BIGINT NOT NULL
);

DO $benchmark$
DECLARE
  workload RECORD;
  actual_row RECORD;
  validation RECORD;
  term_pattern TEXT;
  target_strategy TEXT;
  count_sql TEXT;
  page_sql TEXT;
  original_ids_sql TEXT;
  candidate_ids_sql TEXT;
  base_select TEXT :=
     'SELECT r.id, r.title, s.label, r.first_seen_at '
     || 'FROM bench_records r JOIN bench_sources s ON s.id=r.source_id ';
  source_guard TEXT := '($2::bigint IS NULL OR r.source_id=$2::bigint)';
  old_filter TEXT := '(r.title ILIKE $1 ESCAPE ''!'' OR s.label ILIKE $1 ESCAPE ''!'')';
  or_count_sql TEXT;
  or_page_sql TEXT;
  union_count_sql TEXT;
  union_page_sql TEXT;
  matched_sql TEXT;
  page_old BIGINT[];
  page_new BIGINT[];
  row_count INTEGER;
  total BIGINT;
  tick TIMESTAMPTZ;
  elapsed NUMERIC;
  run_number INTEGER;
  variant_number INTEGER;
BEGIN
  -- Both strategies have the same stable ORDER BY, filter and LIMIT/OFFSET.
  -- UNION (not UNION ALL) eliminates duplicates when both predicates match.
  matched_sql :=
      'SELECT r.id FROM bench_records r '
      || 'WHERE ' || source_guard || ' AND r.title ILIKE $1 ESCAPE ''!'' '
      || 'UNION '
      || 'SELECT r.id FROM bench_records r JOIN bench_sources s ON s.id=r.source_id '
      || 'WHERE ' || source_guard || ' AND s.label ILIKE $1 ESCAPE ''!''';

  or_count_sql := 'SELECT COUNT(*) ' ||
    'FROM bench_records r JOIN bench_sources s ON s.id=r.source_id '
    || 'WHERE ' || source_guard || ' AND ' || old_filter;
  or_page_sql := base_select || 'WHERE ' || source_guard || ' AND ' || old_filter
    || ' ORDER BY r.first_seen_at DESC, r.id DESC LIMIT $3 OFFSET $4';

  union_count_sql := 'SELECT COUNT(*) FROM (' || matched_sql || ') matched';
  union_page_sql := base_select || 'JOIN (' || matched_sql || ') matched ON matched.id=r.id '
    || 'ORDER BY r.first_seen_at DESC, r.id DESC LIMIT $3 OFFSET $4';

  original_ids_sql :=
    'SELECT r.id FROM bench_records r JOIN bench_sources s ON s.id=r.source_id '
    || 'WHERE ' || source_guard || ' AND ' || old_filter;
  candidate_ids_sql := matched_sql;

  FOR workload IN SELECT * FROM sb55_workloads ORDER BY case_name LOOP
    -- Escape the same metacharacters as CatalogRepository.findRecords().
    term_pattern := '%' || replace(replace(replace(workload.search_text, '!', '!!'), '%', '!%'), '_', '!_') || '%';

    -- Validate TOTAL matching IDs, not only counts or the first page.
    EXECUTE
      'WITH original AS (' || original_ids_sql || '), '
      || 'candidate AS (' || candidate_ids_sql || ') '
      || 'SELECT (SELECT COUNT(*) FROM original) AS original_count, '
      || '       (SELECT COUNT(*) FROM candidate) AS candidate_count, '
      || '       (SELECT COUNT(*) FROM (SELECT id FROM original EXCEPT ALL SELECT id FROM candidate) d) AS missing_ids, '
      || '       (SELECT COUNT(*) FROM (SELECT id FROM candidate EXCEPT ALL SELECT id FROM original) d) AS extra_ids'
    INTO validation USING term_pattern, workload.source_id;

    -- Validate pagination order as well as membership. Exact same LIMIT/OFFSET.
    EXECUTE 'SELECT COALESCE(array_agg(id ORDER BY first_seen_at DESC, id DESC), ARRAY[]::bigint[]) FROM ('
      || or_page_sql || ') q'
    INTO page_old
    USING term_pattern, workload.source_id, workload.page_size, workload.page_number * workload.page_size;

    EXECUTE 'SELECT COALESCE(array_agg(id ORDER BY first_seen_at DESC, id DESC), ARRAY[]::bigint[]) FROM ('
      || union_page_sql || ') q'
    INTO page_new
    USING term_pattern, workload.source_id, workload.page_size, workload.page_number * workload.page_size;

    INSERT INTO sb55_parity VALUES (
      workload.case_name, validation.original_count, validation.candidate_count, validation.missing_ids, validation.extra_ids,
      page_old = page_new
    );

    IF validation.missing_ids <> 0 OR validation.extra_ids <> 0 OR validation.original_count <> validation.candidate_count OR page_old IS DISTINCT FROM page_new THEN
      RAISE EXCEPTION 'Search regression detected for % (original %, candidate %, missing %, extra %, page_equal %)',
        workload.case_name, validation.original_count, validation.candidate_count, validation.missing_ids, validation.extra_ids, page_old=page_new;
    END IF;

    -- Run OR and UNION in alternating order, with one warm-up per variant.
    -- Each timed call models the API's COUNT(*) + LIMIT/OFFSET data lookup.
    FOR run_number IN 0..9 LOOP
      FOR variant_number IN 1..2 LOOP
        IF (run_number + variant_number) % 2 = 0 THEN
          target_strategy := 'OR';
          count_sql := or_count_sql;
          page_sql := or_page_sql;
        ELSE
          target_strategy := 'UNION';
          count_sql := union_count_sql;
          page_sql := union_page_sql;
        END IF;

        tick := clock_timestamp();
        EXECUTE count_sql INTO total USING term_pattern, workload.source_id;
        row_count := 0;
        FOR actual_row IN EXECUTE page_sql USING term_pattern, workload.source_id, workload.page_size, workload.page_number * workload.page_size LOOP
          row_count := row_count + 1;
        END LOOP;
        elapsed := 1000 * EXTRACT(EPOCH FROM clock_timestamp() - tick);
        -- Exclude run 0 (warm-up) from statistical aggregation.
        IF run_number > 0 THEN
          INSERT INTO sb55_samples VALUES (workload.case_name, target_strategy, run_number, round(elapsed, 3), row_count, total);
        END IF;
      END LOOP;
    END LOOP;
  END LOOP;
END
$benchmark$;

\echo '=== PARITY: every case must have zero missing/extra IDs and page_identical = t ==='
SELECT * FROM sb55_parity ORDER BY case_name;

\echo '=== BENCHMARK: 9 timed runs per strategy; median and p95 are in milliseconds ==='
SELECT case_name, strategy, COUNT(*) AS runs,
       ROUND(AVG(time_ms),3) AS mean_ms,
       ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY time_ms))::numeric,3) AS median_ms,
       ROUND((percentile_cont(0.95) WITHIN GROUP (ORDER BY time_ms))::numeric,3) AS p95_ms,
       MIN(returned_count) AS min_page_rows, MAX(total_count) AS total_matching_records
FROM sb55_samples
GROUP BY case_name, strategy ORDER BY case_name, strategy;

\echo '=== RATIO: median OR / median UNION (> 1 favors UNION; < 1 favors OR) ==='
WITH medians AS (
  SELECT case_name, strategy,
    (percentile_cont(0.5) WITHIN GROUP (ORDER BY time_ms))::numeric AS median_ms
  FROM sb55_samples GROUP BY case_name, strategy
)
SELECT o.case_name, ROUND(o.median_ms,3) AS or_median_ms,
       ROUND(u.median_ms,3) AS union_median_ms,
       ROUND(o.median_ms / NULLIF(u.median_ms, 0),2) AS speed_ratio
FROM medians o JOIN medians u USING(case_name)
WHERE o.strategy='OR' AND u.strategy='UNION'
ORDER BY o.case_name;

\echo '=== Phase 5.5 comparison finished (only TEMP tables were created). ==='
