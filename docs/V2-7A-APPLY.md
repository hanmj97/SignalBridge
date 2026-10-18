# SignalBridge V2-7A — Two-domain catalog and recruitment partner readiness

## Scope (fixed by product owner, 2026-10-18)

SignalBridge public catalog content consists **only** of:

1. Government/municipal policies **for young people in their 20s or 30s**: youth cash benefits, employment assistance, jobs programs and relevant support. Never guess individual eligibility from age labels alone.
2. Recruitment postings from officially permitted providers: Saramin, JobKorea, Wanted, Jasoseol.com, etc.

No generic company-grant API, unrelated open data, or GitHub Releases as public product data. Previous Engineering screen and old GitHub source/notification system remain untouched.

## This patch actually does

- Adds `scope=YOUTH|JOB` to existing `/api/v1/opportunities` list without removing legacy `kind` queries.
- Restricts **all** opportunity list and detail reads to reviewed provider+kind combinations. This is a catalog **read restriction**, not a database deletion or a full suitability audit.
- Hides `yongin_jobs_official` and generic business-support datasets from the public catalog. The existing Yongin importer, lab database rows and paused Airflow DAG are preserved for development.
- Creates two public tabs (`2030 청년 지원정책`, `채용공고`) rather than four top-level information types. Programs and benefits can appear only inside the youth policy group **and only from reviewed youth-policy source keys**.
- Keeps portal-based right-side drawer, search, region filter, login, subscriptions, CSS/theme and provider detail links.
- Shows `sourceLabel` on non-demo cards, instead of inaccurately calling all non-demo rows '공공데이터'.
- Updates stale notification copy; does **not** activate or modify Discord or Airflow scheduling.
- Corrects registry: JobKorea official API exists but its access requires provider review, prioritizing public institutions/schools. Removes non-target generic SME grant feed from future provider plan.

**Allowed source keys for actual public records** are currently: `youthcenter_policies_official`, `saramin_jobs_official`, `jobkorea_jobs_official`, `wanted_jobs_official`, `jasoseol_jobs_official`. Adding another provider requires intentional code/test change after API permissions and relevant content scope are verified. `v2_demo_opportunities` may appear as visibly synthetic training rows if `includeDemo=true`; production deployment should leave `includeDemo=false`.

**Important:** Existing Saramin V2-6A supports read-only inspection, not database writes; do not assume official API approval or republishing permission. `saramin_jobs_official` is an anticipated source_key, not proof of a connected data source. Youthcenter real field mapping is likewise pending approved-response validation.

## Apply safely in Windows PowerShell

```powershell
# Move to project root.
Set-Location 'D:\Projects\SignalBridge'

# Back up all files that this patch replaces, including tests and provider registry.
$backupDir = Join-Path $HOME ('SignalBridge-PrivateBackups\pre-v2-7a-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$files = @(
  'backend\src\main\java\dev\signalbridge\api\opportunities\OpportunityRepository.java',
  'backend\src\main\java\dev\signalbridge\api\opportunities\OpportunityController.java',
  'backend\src\test\java\dev\signalbridge\api\opportunities\OpportunityControllerTest.java',
  'frontend\src\api.ts',
  'frontend\src\OpportunitiesPanel.tsx',
  'frontend\src\OpportunityAlertsPanel.tsx',
  'frontend\src\styles.css',
  'collector\providers\registry.py'
)
foreach ($file in $files) {
  $dest = Join-Path $backupDir $file
  New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
  Copy-Item -LiteralPath $file -Destination $dest
}

# Apply the downloaded ZIP. Do NOT edit .env and do not expose access keys.
Expand-Archive -Path (Join-Path $HOME 'Downloads\SignalBridge-V2-7A-Two-Category-Catalog.zip') -DestinationPath (Get-Location) -Force

# Run tests offline before rebuilding containers.
.\scripts\check-v2-7a.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline test failed' }
```

## Rebuild and verify

For the existing project, use the full set of Compose overlays that you have already enabled; the current final overlay is `docker-compose.v2-5c.yml`. API container **must be rebuilt** because its SQL catalog restriction changes; React Vite source refresh may hot reload but still run the type check/bundle:

```powershell
# Existing compose overlays; order matters.
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

# Check compose without echoing sensitive values.
docker compose @compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose validation failed' }

# Rebuild only the API container. No migration or database reset required.
docker compose @compose up -d --no-deps --build api
if ($LASTEXITCODE -ne 0) { throw 'Spring API build failed' }

# Frontend TypeScript + production bundle check.
docker compose @compose exec web npm run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }

# API smoke test: two official public scopes. Use localhost port 8081.
Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/opportunities?scope=YOUTH&includeDemo=true' | Select-Object totalElements
Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/opportunities?scope=JOB&includeDemo=true' | Select-Object totalElements

# Invalid scope must produce HTTP 400 (normal rejection).
try {
  Invoke-WebRequest -Uri 'http://localhost:8081/api/v1/opportunities?scope=BUSINESS_GRANTS'
  throw 'INVALID SCOPE WAS NOT REJECTED'
} catch {
  if ($_.Exception.Response.StatusCode.value__ -ne 400) { throw }
}
```

Open http://localhost:5173. Check two tabs and the right-edge detail drawer. Source-filtered listings may contain **only labelled demo rows** until officially permitted new sources start importing. If the page is empty, verify with `includeDemo=true` and the source keys in `signalbridge_v2_lab`, not by disabling the source scope guard.

### Important distinction

`scope=YOUTH` is a **provider + record-type allowlist**, not an algorithm that decides if a particular policy truly fits a 20–30 year-old applicant. Before public launch, the Youthcenter ingestion adapter must verify and store its explicit audience/age fields. Generic youth-labeled enterprise schemes must not be misrepresented as personal benefits. `scope=JOB` is similarly restricted to reviewed recruitment API source keys.

## Official provider status as of 2026-10-18

- **Saramin:** Official job-search API exists; access-key after application approval. https://oapi.saramin.co.kr/guide/info
- **JobKorea:** Official recruitment and entry-level API exists; preferentially available to public institutions or schools. Individual/company applications subject to internal review. https://www.jobkorea.co.kr/service/api
- **Wanted / Jasoseol.com:** We have not verified public third-party recruitment-data API docs. Do not reverse-engineer private web endpoints or scrape without permission.
- **Youthcenter:** Official youth-policy API application pending; the fixture is synthetic and does not prove live response field mapping.

## Rollback

Restore your backed-up eight files from `$backupDir` (preserving their relative paths) and rebuild `api`. No database migration and no destructive data change occurs in V2-7A.
