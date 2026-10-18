# SignalBridge V2-7B — Yongin recruitment-only launch path

## Product boundaries

- Public homepage remains **only two tabs**: (1) policies and benefits intended for people in their twenties/thirties, and (2) recruitment notices. No generic company grants.
- Saramin and JobKorea still **lack approved access keys** in this project (confirmed by user on 2026-10-18). Their adapters stay inactive. Wanted/Jasoseol third-party access is unverified. Youthcenter policy key remains under approval.
- Yongin City jobs/exams public API **key is approved** (user confirmation). Its official `type=채용정보` filter is documented at https://www.data.go.kr/data/15097328/openapi.do and the dataset lists unrestricted use. This project still checks item types to prevent exam/result announcements being saved as jobs.
- Only the **Yongin `채용정보` category** is added to the public job list. Previously imported 시험 정보/시험정보 entries remain in LAB DB but are hidden in **both list and detail APIs**. No deleting or rewriting those rows. Existing demos remain visibly fake.
- Dataset API does not guarantee an individual recruitment posting URL: `link_kind=DATASET` and disclosure on the existing right-edge drawer remain unchanged. Do not market the dataset page as an application link.
- This data **does not imply youth eligibility**. Recruitment notices may cover other ages. Age/region are left unknown until explicit API data exists.
- Airflow daily collector stays guarded by `SIGNALBRIDGE_V2_LIVE_INGEST_ENABLED` and paused by default. No changes to member DB or Discord alert settings. Existing notifier still excludes data-set-only links.

## Changed files

- `collector/providers/yongin_jobs.py`: sends documented optional `type=채용정보` to the official API.
- `collector/yongin_v25b.py`: independent category filter before LAB writes; rejects non-job direct mapping; updates only original verified source label.
- `backend/.../OpportunityRepository.java`: opt in `yongin_jobs_official` only when `kind=JOB`, `category=채용정보`, `link_kind=DATASET`, `is_demo=false`.
- `collector/providers/registry.py`: update user-confirmed status/label.
- `collector/tests/test_v27a_service_scope.py`: updates previous no-Yongin contract to precise recruitment-only guard.
- `collector/tests/test_v27b_yongin_jobs_only.py` and `scripts/check-v2-7b.ps1`: offline regression tests.

**No DB migration, table DROP, .env overwrite, React changes, or secret files.** 

## Apply

```powershell
# Go to the project and inspect user-modified files first.
Set-Location 'D:\Projects\SignalBridge'
git status --short

# Back up only files this patch overwrites, preserving relative paths.
$backup = Join-Path $HOME ('SignalBridge-PrivateBackups\pre-v27b-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$files = @(
  'backend\src\main\java\dev\signalbridge\api\opportunities\OpportunityRepository.java',
  'collector\providers\yongin_jobs.py',
  'collector\yongin_v25b.py',
  'collector\providers\registry.py',
  'collector\tests\test_v27a_service_scope.py'
)
foreach ($file in $files) {
  $target = Join-Path $backup $file
  New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
  Copy-Item -LiteralPath $file -Destination $target
}

# Apply approved recruitment-only patch (assumes V2-7A installed).
Expand-Archive -Path (Join-Path $HOME 'Downloads\SignalBridge-V2-7B-Yongin-Jobs-Only.zip') -DestinationPath (Get-Location) -Force

# Check offline first (no API requests / DB writes).
.\scripts\check-v2-7b.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline validation failed' }
```

## API preview and LAB write

```powershell
# Read-only inspection (API key from existing local .env, never paste it into chat).
py -3 -m collector.yongin_scheduler_v25c --live --permit-network --pages 3 --rows 20
if ($LASTEXITCODE -ne 0) { throw 'Read-only API preview failed' }

# Write only approved type=채용정보 rows to isolated signalbridge_v2_lab.
py -3 -m collector.yongin_scheduler_v25c --live --permit-network --pages 3 --rows 20 --write-lab
if ($LASTEXITCODE -ne 0) { throw 'LAB write failed' }
```

Existing LAB records with `category=시험 정보` are not deleted, but cannot appear in the public catalog (even via record-detail ID). Stored real records are kept in `signalbridge_v2_lab` and retain change history/version control.

## Rebuild and verify

```powershell
# Match previous V2 overlay order exactly.
$compose = @(
  '-f','docker-compose.yml',
  '-f','docker-compose.phase2.yml',
  '-f','docker-compose.phase3.yml',
  '-f','docker-compose.search-experiment.yml',
  '-f','docker-compose.phase6.yml',
  '-f','docker-compose.phase10-email.yml',
  '-f','docker-compose.phase11-discord.yml',
  '-f','docker-compose.v2-2.yml',
  '-f','docker-compose.v2-3.yml',
  '-f','docker-compose.v2-5c.yml'
)

# Avoid dumping secrets via `docker compose config` without --quiet.
docker compose @compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose failed' }

# Rebuild API only, because the JDBC catalog allowlist changed.
docker compose @compose up -d --no-deps --build api
if ($LASTEXITCODE -ne 0) { throw 'Spring Boot build failed' }

# React source unchanged; check frontend build anyway.
docker compose @compose exec web npm run build
if ($LASTEXITCODE -ne 0) { throw 'React build failed' }

# Verify official live JOB results including non-demo rows.
$jobs = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/opportunities?scope=JOB&includeDemo=false'
$jobs | Select-Object totalElements
$jobs.items | Select-Object -First 5 title,sourceLabel,category,linkKind,isDemo
```

In DBeaver connect specifically to `signalbridge_v2_lab` and run:

```sql
SELECT d.category, d.link_kind, count(*) AS records
FROM opportunity_details d
JOIN records r ON r.id=d.record_id
JOIN sources s ON s.id=r.source_id
WHERE s.source_key='yongin_jobs_official'
GROUP BY d.category,d.link_kind
ORDER BY d.category;
```

Check frontend `http://localhost:5173` -> `채용공고`, and select a Yongin posting to confirm the drawer says `공공데이터 제공 페이지` rather than `지원하기`. If public results are empty, first check whether the API returned any `채용정보` records and that earlier V2-5B migration 017 applied.

Airflow's Python `collector` source is bind-mounted read-only; it sees this filter without an image rebuild. Keep the DAG paused until manually reviewed. The existing member Discord alert job is unchanged.

## Revert without touching records

Restore five backed-up files preserving relative paths from `$backup`, then rebuild `api`. No database rollback is needed because V2-7B makes no schema changes and does not delete source records.
