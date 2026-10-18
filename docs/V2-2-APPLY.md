# SignalBridge V2-2 — Opportunity Catalog API & UI

## Goal

Add a user-facing youth policy/benefit/job catalog in React with a **separate local V2 lab database connection**. Keep the existing account system, Spring Boot primary datasource, Airflow DAGs, Discord alerts and original release-engineering dashboard intact.

## Before applying

- V2-1 tested in `signalbridge_v2_lab`: 3 synthetic records plus version history.
- `feature/youth-policy-platform` branch; all previous changes committed or otherwise backed up.
- No credentials or `.env` included in this package.
- This is **demo data, not real public opportunities**. The UI disables official-application links on demo cards and prominently labels them as synthetic.

## Changed files

- Adds Java `opportunities/*`: read-only search/detail/version-history GET endpoints.
- Adds `docker-compose.v2-2.yml`: sets `SIGNALBRIDGE_V2_JDBC_URL` for **api** service only.
- Adds React `OpportunitiesPanel.tsx`, updates `App.tsx`, `api.ts` and `styles.css`.
- Adds Java controller tests and `scripts/check-v2-2.ps1`.
- Existing endpoints are unchanged; old release dashboard is accessible via **Engineering · 릴리스** menu.
- Spring Boot does not register a second DataSource bean. The new repository uses a local JDBC client for V2 queries only. This is intentionally small-scale for lab use, not a production-ready pool.

## API contracts

- `GET /api/v1/opportunities?page=0&size=12&q=...&kind=JOB&region=경기&includeDemo=true`
- `GET /api/v1/opportunities/{id}?includeDemo=true`
- When `includeDemo` is omitted or false, demo rows are not returned.
- Default no V2 JDBC URL configured: HTTP 503, never silently use the legacy database.
- Only read-only SELECT; API never writes to lab.
- `isDemo` flag is always included in results.
- Search is parameterized and escapes LIKE metacharacters.

## Lab limitations

- No actual Ontong Youth API fetch, no real recruitment integration yet.
- `region` filter is an exact value in `region_codes` (no hierarchical administrative-code matching yet).
- Status is reported as provider supplied and not inferred from deadline.
- No V2 bookmarking or V2 member alerts yet; existing watchlist is legacy GitHub releases.
- Java repository's lab connection is a simple JDBC datasource for small test load only; replace with bounded Hikari pool and proper migrations before public deployment.
- API expose demo records when explicitly requested with `includeDemo=true`. Do not use this configuration for a public production launch.

## Testing and applying

Detailed PowerShell commands are provided in the corresponding ChatGPT message. Use `docker compose` with all existing overlay files and append `-f docker-compose.v2-2.yml` LAST. Rebuild only API service, build React and run `./scripts/check-v2-2.ps1`.

## Rollback

1. Restore backed-up `App.tsx`, `api.ts`, `styles.css`.
2. Start API WITHOUT `docker-compose.v2-2.yml`, using the previous overlays.
3. This patch includes no migrations or DB writes. Existing lab and main database remain untouched.
