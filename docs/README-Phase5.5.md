# SignalBridge — Phase 5.5 SQL search benchmark (safe additive patch)

## Purpose

Benchmark the **existing** `OR` search against a **candidate** `UNION` rewrite on the isolated `signalbridge_perf` database. Do not change Spring Boot Repository yet. It may be faster for rare title searches but **slower for broad title or source-name searches**.

**Files added only:**
- `perf-lab/run-phase55.ps1`
- `perf-lab/sql/06_compare_search.sql`
- `perf-lab/sql/07_compare_plans.sql`
- `docs/README-Phase5.5.md`

The SQL code uses existing Phase 5 benchmark data and `pg_trgm` index. It does not rebuild or truncate the dataset. The comparison script creates **temporary tables only**, which disappear when the psql connection closes.

## What is tested

| Scenario | Query | Expected rough selectivity |
| --- | --- | --- |
| rare_title | RareRollbackAlert | rare |
| medium_title | Backend change | a few thousand |
| common_title | General release | very common |
| source_label | Grafana | one complete source |
| filtered_label | Grafana with `source_id=3` | scoped to one source |
| no_match | not-found-zxq913 | no results |
| literal_wildcard | `%_!` | escaped as literal characters |
| mixed_case_source | gRaFaNa | case-insensitive, second page |

**Correctness first:** Compare the FULL matching ID set with `EXCEPT ALL`, total COUNT and stable ORDER BY + LIMIT/OFFSET page IDs. Script raises an SQL exception on any mismatch.

**Timing:** 1 untimed warmup + 9 runs per strategy, alternating OR and UNION order. Capture API-style `COUNT(*)` **plus** page query (not just the page query). Report means, medians, p95 (only 9 samples, directional, not statistically conclusive), and median speed ratio. Separately save EXPLAIN ANALYZE/BUFFERS for rare-title and source-label searches.

Results depend on CPU, OS, Docker resources, warm cache, key distribution, PostgreSQL planner and data volume. **Do not assume UNION always wins**. If a strategy is better for one case but worse for another, investigate before changing a production query.

## Run

```powershell
# From project root (D:\Projects\SignalBridge)
.\perf-lab\run-phase55.ps1
```

Then share both files:

- `perf-lab/reports/06_compare_search.txt`
- `perf-lab/reports/07_compare_plans.txt`

## Next development step after reviewing reports

1. Decide a query strategy based on selectivity and correctness (may retain OR).
2. If warranted, implement a gated Spring Boot Repository improvement with parity/integration tests in a separate patch.
3. Validate API count, stable ordering, paging, wildcard escaping, and sourceId filtering on real data.
4. Only then consider a production `pg_trgm` GIN index. Production index DDL needs separate safety review and backups.

## Notes

- The public production DB is `signalbridge`. This script **only** connects to `signalbridge_perf`.
- Do not run Phase 5 setup again unless you explicitly want to regenerate synthetic rows.
- Existing React V2.4 theme, motion effects, Spring Boot API, collector and Docker Compose remain unchanged.
