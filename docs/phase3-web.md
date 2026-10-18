# SignalBridge — Phase 3: React + TypeScript public dashboard

This add-on keeps the existing Phase 1 PostgreSQL and Phase 2 API intact. It has **no fake hardcoded release dataset**: it always requests data from your own Spring Boot API backed by the PostgreSQL collector records.

## Merge these files into your existing project

Unzip `SignalBridge-Phase3-AddOn.zip` at `D:\Projects\SignalBridge`. The archive adds only `frontend/`, `docker-compose.phase3.yml`, and `docs/phase3-web.md`. It does not contain or overwrite `.env`, database schema, collector or API files.

## Windows 11 PowerShell — local Docker dev

```powershell
# Go to the existing project root
Set-Location 'D:\Projects\SignalBridge'

# IMPORTANT: use the three files together, including your Phase 2 file.
# The host 8081 -> container 8080 change from the previous phase stays untouched.
# Compose provides internal DNS name api:8080 for Vite's /api reverse proxy.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml config

# Start old database + API + new web service (without deleting existing data).
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml up -d

# On first run the web container installs npm packages, which can take a while.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml logs -f web

# Check all container statuses in a second PowerShell window.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml ps

# Check the Spring Boot API directly on the Windows host's port 8081.
Invoke-RestMethod -Uri 'http://localhost:8081/actuator/health'

# Check browser origin proxy for real API response.
Invoke-RestMethod -Uri 'http://localhost:5173/api/v1/sources' | ConvertTo-Json -Depth 5
```

Open **http://localhost:5173**.

## What you'll see

- List of your actual saved GitHub public releases (paginated, 10 per page)
- Client-side input with server-side title/source search (`GET /api/v1/records?q=...`)
- Source filter buttons backed by `GET /api/v1/sources`
- Detailed release drawer loaded from `GET /api/v1/records/{id}`
- Version/change history loaded from `GET /api/v1/changes`
- Data last-checked time sourced from `sources.last_checked_at`
- Error, no-results, loading, navigation, responsive mobile layout

**Note:** Browser `localhost:5173` talks to Vite, whose reverse proxy forwards `/api/*` to the service name `api:8080` on the Docker Compose network. You do **not** need to change your Phase 2 API port from 8081 back to 8080. Backend's container port is still 8080.

**Not yet included:** Accounts, subscription alerts, automatic periodic scheduling, public-domain hosting, official government data sources. The UI is a **local development preview**; it's not an online public website yet. Later deploy with HTTPS, auth where necessary, TLS termination, rate limits, and an appropriate public API source/data usage policy.

## Unit/build smoke tests

To run production compilation checks (Node lives in the Docker container):

```powershell
# Builds frontend and checks types using locally installed Node dependencies.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml exec web npm run build
```

## Safety

- Do not use `docker compose down -v`: it deletes the named database volume.
- Dev frontend binds only to 127.0.0.1:5173 and is not production hardened.
- Data refresh button reloads server data; it does **not** trigger data ingestion. Re-run the Python collector to update stored data.
- A source's last checked time is not necessarily when its content changed.
- Version 1 entries are initial discoveries, not content modifications.
