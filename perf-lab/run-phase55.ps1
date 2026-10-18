# SignalBridge Phase 5.5, safe repeatable search benchmark.
# Reads ONLY signalbridge_perf in existing Docker container.
# Does not change production schema, PostgreSQL volumes, frontend, or backend.
$ErrorActionPreference = 'Stop'

$containerName = 'signalbridge-postgres'
$dbUser = 'signalbridge'
$dbName = 'signalbridge_perf'
$labDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlDir = Join-Path $labDir 'sql'
$reportsDir = Join-Path $labDir 'reports'
New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null

# Preflight: check Docker and PostgreSQL container status.
$running = docker inspect --format '{{.State.Running}}' $containerName 2>$null
if ($LASTEXITCODE -ne 0 -or ($running | Out-String).Trim() -ne 'true') {
    throw "Docker container '$containerName' is not running. Start SignalBridge first."
}

# Do not create or silently fall back to production database.
$dbExists = docker exec $containerName psql -X -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$dbName';"
if ($LASTEXITCODE -ne 0 -or ($dbExists | Out-String).Trim() -ne '1') {
    throw "Expected isolated database '$dbName' was not found. Run Phase 5 first."
}

# Check test tables, expected rows and trigram index from Phase 5.
$preflightSql = "SELECT CASE WHEN to_regclass('public.bench_records') IS NOT NULL AND to_regclass('public.bench_sources') IS NOT NULL AND to_regclass('public.idx_bench_records_title_trgm') IS NOT NULL THEN 1 ELSE 0 END;"
$preflight = docker exec $containerName psql -X -U $dbUser -d $dbName -tAc $preflightSql
if ($LASTEXITCODE -ne 0 -or ($preflight | Out-String).Trim() -ne '1') {
    throw 'Phase 5 benchmark tables or trigram index missing in signalbridge_perf. Rerun Phase 5 first.'
}

$rows = docker exec $containerName psql -X -U $dbUser -d $dbName -tAc 'SELECT COUNT(*) FROM bench_records;'
if ($LASTEXITCODE -ne 0 -or [long](($rows | Out-String).Trim()) -lt 150000) {
    throw 'Expected at least 150,000 benchmark rows in signalbridge_perf. Rerun Phase 5 first.'
}

Write-Host "Running Phase 5.5 with $($rows | Out-String) benchmark records in '$dbName'." -ForegroundColor Cyan

# Execute two read-only SQL files. TEMP tables in 06 are session-scoped.
$steps = @(
    @{ Source = '06_compare_search.sql'; Report = '06_compare_search.txt' },
    @{ Source = '07_compare_plans.sql'; Report = '07_compare_plans.txt' }
)
foreach ($step in $steps) {
    $sourceFile = Join-Path $sqlDir $step.Source
    if (-not (Test-Path $sourceFile)) { throw "Missing local SQL file: $sourceFile" }
    $targetFile = "/tmp/sb55_$($step.Source)"
    Write-Host "`n=== $($step.Source) ===" -ForegroundColor Cyan

    # Copy only the benchmark SQL to the Docker container.
    docker cp $sourceFile "${containerName}:$targetFile"
    if ($LASTEXITCODE -ne 0) { throw "Docker copy failed: $($step.Source)" }

    # Capture stdout in Windows report. NOTICE messages on stderr are not failures.
    # -X: ignore .psqlrc; -v ON_ERROR_STOP=1: SQL errors produce a non-zero exit code.
    docker exec $containerName psql -X -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $targetFile |
        Tee-Object -FilePath (Join-Path $reportsDir $step.Report)
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $($step.Source). Please share console error." }
}

Write-Host "`nPhase 5.5 completed. Reports saved to: $reportsDir" -ForegroundColor Green
Write-Host "No production database, backend code, or frontend files were modified."
