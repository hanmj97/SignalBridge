# SignalBridge — architecture, v0.1

## Vision
- Public visitors see searchable, source-attributed information with a verified-at timestamp.
- Signed-in users (future phase) follow items and receive update alerts.
- Operators inspect ingestion runs, data freshness, DB health, and changes.
- AI (future phase) can summarize changes with citations, never define the canonical truth.

## Phase 1 (included in this starter)

GitHub public Releases API → Python collector → PostgreSQL

Main tables:
- `sources`: source registry, GitHub repository identifier, ETag, last check.
- `records`: current normalized state, unique `(source_id, external_id)`.
- `record_versions`: durable NEW and UPDATED snapshots, ordered versions.
- `ingestion_runs`: SUCCESS / FAILED / NOT_MODIFIED inspection.

Operational design:
- No crawler or arbitrary external URL entry points. Source registry is admin-owned.
- HTTP timeout, status verification, conditional requests (ETag), API rate-limit awareness.
- Stable content hashing ignores mutable GitHub download counters.
- SQL unique keys plus row locks prevent common concurrent duplicate/version races.
- DB writes per source are atomic. Failures recorded separately for troubleshooting.
- A 304 response means the upstream listing has not changed; no full fetch.
- The collector currently examines the latest 20 published releases only, not all historical releases or deletions.

## Planned architecture

Public sources/API → Python/Airflow → PostgreSQL ← Spring Boot REST API ← React
                                                ↑
                                      Admin monitoring UI
                                                ↓
                                          Alert workers

- Spring Boot 3 + Java 21, auth, search, pagination, source view and ops API.
- React+TypeScript public web frontend and restricted ops pages.
- Airflow DAG for hourly or longer polls with retry/backoff; frequency must fit rate limits.
- AI summaries as separate generated material with explicit provenance and safeguards.
- Additional official public-data adapters only after terms/API access are verified.
- MSSQL stays an isolated integration/DBA testing environment until truly needed.

## Phase 2 / security requirements
- Restrict admin endpoints and secrets, add per-user rate limiting.
- Use carefully scoped public-data licenses, preserve source attribution.
- Never expose tokens or database to the public Internet.
- Avoid storing raw visitor IP addresses without separate privacy analysis.
- Handle deleted/withdrawn source items as a separate lifecycle feature.
- Before production: backups and restore drills, TLS, monitoring, alert retries and idempotence.
