-- SignalBridge / PostgreSQL: use in DBeaver after the Phase 9 migration.
-- Read only: does not change the database.

-- Check whether Phase 9 account and watch tables exist.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('app_users','watch_rules','watch_events','sources','records','record_versions','ingestion_runs')
ORDER BY table_name;

-- Review primary keys, foreign keys and unique constraints.
SELECT tc.table_name,
       tc.constraint_type,
       tc.constraint_name,
       kcu.column_name,
       ccu.table_name AS referenced_table,
       ccu.column_name AS referenced_column
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.key_column_usage kcu
       ON kcu.constraint_name = tc.constraint_name
      AND kcu.constraint_schema = tc.constraint_schema
LEFT JOIN information_schema.constraint_column_usage ccu
       ON ccu.constraint_name = tc.constraint_name
      AND ccu.constraint_schema = tc.constraint_schema
WHERE tc.table_schema = 'public'
  AND tc.table_name IN ('app_users','watch_rules','watch_events')
  AND tc.constraint_type IN ('PRIMARY KEY','FOREIGN KEY','UNIQUE')
ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name, kcu.ordinal_position;

-- Review indexes, including the owner-scoped uniqueness constraint.
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('app_users','watch_rules','watch_events')
ORDER BY tablename, indexname;

-- Safe aggregated account/watch counts (no email or password hashes returned).
SELECT (SELECT COUNT(*) FROM app_users) AS member_count,
       (SELECT COUNT(*) FROM watch_rules WHERE user_id IS NULL) AS legacy_rule_count,
       (SELECT COUNT(*) FROM watch_rules WHERE user_id IS NOT NULL) AS member_rule_count;
