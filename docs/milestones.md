# Milestones & acceptance tests

## P1 — First real data in DB (this package)
- [ ] Docker PostgreSQL reports healthy
- [ ] Python dependencies installed, unit tests pass
- [ ] GitHub Releases API fetches actual records
- [ ] First run creates version=1 rows
- [ ] Second run adds no duplicate records or versions if unchanged
- [ ] `ingestion_runs` records success/unchanged/failure

## P2 — Web MVP
- [ ] Java 21 Spring Boot: GET /api/v1/records (paged), GET /api/v1/records/{id}, GET /api/v1/changes
- [ ] React+TypeScript: source tabs, latest releases, change history, source URLs
- [ ] Search + source filtering + last-checked indicators

## P3 — Real public user value
- [ ] Official government data adapter where usage rights verified
- [ ] User sign-up/login, watch rules and alert subscriptions
- [ ] Email/Discord alert delivery with retry and dedupe
- [ ] Clear privacy/terms/source attribution pages

## P4 — Deep engineering portfolio
- [ ] Airflow orchestration and data-freshness SLA monitoring
- [ ] Index/query tuning evidence and EXPLAIN ANALYZE
- [ ] Transaction/concurrent write tests
- [ ] Backup, restore drills, and failure injection
- [ ] Optional MSSQL integration and AI change summaries
- [ ] Publish app, README architecture, screenshots, benchmarks
