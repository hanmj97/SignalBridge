# SignalBridge V2-5C — Daily Yongin collection readiness

## Scope
- Adds a batch/page coordinator (`collector/yongin_scheduler_v25c.py`) that reuses the existing V2-5B provider mapping and isolated LAB database writer.
- Adds **paused-by-default** `signalbridge_yongin_live_daily` Airflow DAG (`09:00 KST` daily when unpaused); explicit environment gate also defaults to `false`.
- Adds Compose overlay `docker-compose.v2-5c.yml` passing existing private Yongin key through the Airflow environment. Does **not** modify main DB, Discord delivery, legacy DAGs, or React.
- This is one provider only, not a complete unified multi-API integration. Other providers require separate permissions and adapters.

## Requirements
- V2-5B migration `017_opportunity_link_kind.sql` already applied in `signalbridge_v2_lab`.
- Existing live Yongin preflight/import worked locally.
- API key in local `.env`: `YONGIN_API_SERVICE_KEY=...` (do not commit or paste secret).
- Airflow container from existing Compose; collector mounted at `/opt/airflow/collector`.

## Apply
1. `Set-Location 'D:\Projects\SignalBridge'`
2. Back up current LAB DB, and run the existing main DB backup if desired.
3. Unzip ZIP **into existing project folder**. Only new files are included.
4. `./scripts/check-v2-5c.ps1` (offline)
5. Compose overlay added as **last file** to your existing `@compose` array, e.g., `'-f','docker-compose.v2-5c.yml'`.
6. `docker compose @compose config --quiet` (avoid printing config; it may contain keys).
7. `docker compose @compose up -d --no-deps --force-recreate airflow`. This restarts the Airflow container, not PostgreSQL or the API.
8. `docker compose @compose exec airflow airflow dags list-import-errors`
9. `docker compose @compose exec airflow airflow tasks list signalbridge_yongin_live_daily`

## Live test (optional; gated)
- Preview on Windows, **no DB writing**: `py -3 -m collector.yongin_scheduler_v25c --live --permit-network --pages 3 --rows 20`
- Confirm page results and dates; original article URLs are *not* guaranteed by this API.
- In local `.env` set `SIGNALBRIDGE_V2_LIVE_INGEST_ENABLED=true`. This only enables a security gate; it does not unpause the DAG.
- Recreate only Airflow via existing `docker compose @compose up -d --no-deps --force-recreate airflow`.
- Trigger `signalbridge_yongin_live_daily` **manually once**, while remaining paused. Verify task logs, LAB `ingestion_runs`, and no Discord member notifications. If valid and you want scheduled daily ingestion, unpause the DAG in Airflow UI. `09:00 KST` schedule is intended; confirm actual next-run timezone in UI.

## Validation and data constraints
- No deletion for records missing from a response page.
- No automatic Discord delivery for `link_kind='DATASET'` rows.
- No inferred candidate eligibility, job location, or article URL.
- Up to 3 pages × 20 rows per scheduled DAG execution. Data feed might be incomplete if more pages exist. Confirm `totalCount`/pagination and improve coverage before real public release.
- Switching on scheduled ingestion consumes API quota. `YONGIN_API_SERVICE_KEY` must be approved and authorized to fetch public job data.

## Additional provider for later: SME support programs
The Ministry of SMEs and Startups *Enterprise Support Program Announcements* API is described at https://www.data.go.kr/data/15157820/openapi.do (dev access auto-approved, production approval required). Its records mostly concern **businesses**; do not classify all entries as youth benefits. Apply for its access separately and capture a *redacted* example response/schema before building adapter and filtering eligibility. Do not hardcode undocumented endpoint fields.
