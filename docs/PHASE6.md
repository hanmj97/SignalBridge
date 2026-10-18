# SignalBridge Phase 6 — Airflow automatic collection (local development)

## What this adds

- Apache Airflow **2.11.0**, in standalone local-development mode on `http://localhost:8082`
- A two-task DAG (`signalbridge_collector_30m`), scheduled **every 30 minutes (Asia/Seoul)**
- Task 1 calls the existing `collector.main()`; task 2 checks freshness of all enabled GitHub sources
- 2 retries with a 3-minute delay; final failure optionally notifies a Discord channel
- Airflow metadata in a **new named Docker volume**, separate from existing PostgreSQL data

This is **NOT a production deployment**: SQLite+SequentialExecutor cannot scale to parallel, high-availability scheduling. Do not expose port 8082 to the internet or commit webhook/token secrets to GitHub.

## Files

- `docker-compose.phase6.yml`: additional local-only Airflow service; only add this file, not replace existing Compose files
- `airflow/Dockerfile` and `airflow/collector-requirements.txt`: extend official Airflow 2.11.0 image with collector dependencies; isolated build context avoids sending your `.env` to Docker build
- `airflow/dags/signalbridge_collector.py`: DAG source
- `airflow/tests/test_dag_contract.py`: offline sanity tests

## Before you start

- Docker Desktop running, existing PostgreSQL service healthy
- Docker memory allocated at least 4 GB (8 GB recommended)
- Port `8082` free on the host
- `.env` already has existing `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- Optional: append `SIGNALBRIDGE_DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...` to local `.env` only (never share it)
- Optional: set `GITHUB_TOKEN` to avoid GitHub unauthenticated rate limits

## Run (PowerShell)

```powershell
# In the existing SignalBridge project directory
Set-Location 'D:\Projects\SignalBridge'

# Run these offline checks before container creation
py -3 -m unittest discover -s .\airflow\tests -v

# Check the merged configuration (avoid sharing output; may include credentials)
docker compose -f docker-compose.yml -f docker-compose.phase6.yml config --quiet

# Bring up just Airflow (and confirm its healthy postgres dependency)
docker compose -f docker-compose.yml -f docker-compose.phase6.yml up -d --build airflow

# Inspect startup without sharing login credentials publicly
docker compose -f docker-compose.yml -f docker-compose.phase6.yml logs --tail=80 airflow

# DAG registration
docker compose -f docker-compose.yml -f docker-compose.phase6.yml exec airflow airflow dags list

# Explicit first run (otherwise next scheduled boundary)
docker compose -f docker-compose.yml -f docker-compose.phase6.yml exec airflow airflow dags trigger signalbridge_collector_30m

# Confirm new run statuses in the existing signalbridge DB
docker compose -f docker-compose.yml -f docker-compose.phase6.yml exec postgres psql -U signalbridge -d signalbridge -c "SELECT s.label, r.status, r.new_count, r.updated_count, r.started_at FROM ingestion_runs r JOIN sources s ON s.id = r.source_id ORDER BY r.id DESC LIMIT 9;"
```

### Browser

1. Open `http://localhost:8082`.
2. Airflow standalone prints generated admin credentials during the first startup. Check startup logs privately; don't paste passwords into chat.
3. Find `signalbridge_collector_30m`; ensure it's **enabled** and trigger it once manually.
4. Confirm both tasks turn **green** (success) and `ingestion_runs` shows `SUCCESS` or `NOT_MODIFIED`.
5. Wait until the next `:00` or `:30` clock boundary to verify automatic runs. Airflow may schedule the first run after the completed 30-minute data interval rather than immediately.

## A few operational notes

- `catchup=False` prevents historical backfills on first startup.
- `max_active_runs=1` prevents multiple scheduled DAG runs overlapping.
- `NOT_MODIFIED` is **healthy** (HTTP 304 means the source content did not change).
- The source collector is idempotent; reruns do not insert duplicate release versions.
- Task 2 is a freshness check. It evaluates `sources.last_checked_at` and does not assert all status fields are successful; Task 1 handles a failed source as a nonzero exit code.
- Discord alerts are sent only after a task reaches final failure (after retries) and only when webhook is configured. Airflow running/automatic scheduling does **not** mean your browser reflects new data immediately: React fetches latest API data on user refresh/navigation.
- The Airflow UI and metadata are separate from your public SignalBridge site. Do not mistake this local Airflow admin interface for a public URL.
- If the DAG does not appear, inspect `airflow dags list-import-errors` and check mounting `airflow/dags`.
- If you receive HTTP 403 from GitHub, check GitHub API rate limits; adding a personal token may help. Never paste tokens in chat.
- If you see authentication redirects/credentials issues, inspect Airflow startup logs. Don't reset/deactivate volumes or `docker compose down -v` to fix routine errors.

## Safe stop

```powershell
# Stop just the Airflow container. PostgreSQL and your web services continue running.
docker compose -f docker-compose.yml -f docker-compose.phase6.yml stop airflow
```

## Next phase

Monitoring collection-failure rates over time, adding job-level metrics to the developer dashboard, and integrating the first allowed public grant/job-posting dataset.
