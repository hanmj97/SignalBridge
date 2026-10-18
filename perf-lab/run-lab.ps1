# SignalBridge Phase 5 - isolated PostgreSQL search performance lab
# Run from any directory: .\perf-lab\run-lab.ps1
# It uses the existing Docker postgres container, without touching the production DB.

$ErrorActionPreference = 'Stop'

# Fixed container/credentials match SignalBridge Starter settings.
$containerName = 'signalbridge-postgres'
$dbUser = 'signalbridge'
$dbName = 'signalbridge_perf'
$labDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlDir = Join-Path $labDir 'sql'
$reportsDir = Join-Path $labDir 'reports'
New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null

# Confirm the existing PostgreSQL container is running.
$running = docker inspect --format '{{.State.Running}}' $containerName 2>$null
if ($LASTEXITCODE -ne 0 -or ($running | Out-String).Trim() -ne 'true') {
    throw "Container '$containerName' is not running. Start SignalBridge Docker first."
}

# Create an isolated benchmark DATABASE only when it does not exist.
# This does NOT recreate or delete the existing signalbridge database or its volume.
$exists = docker exec $containerName psql -X -U $dbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$dbName';"
if ($LASTEXITCODE -ne 0) { throw 'Could not query PostgreSQL database catalog.' }
if (($exists | Out-String).Trim() -ne '1') {
    Write-Host "Creating isolated benchmark database: $dbName"
    docker exec $containerName psql -X -v ON_ERROR_STOP=1 -U $dbUser -d postgres -c "CREATE DATABASE $dbName OWNER $dbUser;"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create performance database.' }
}

# Run SQL scripts in order. Copy each script to the container first.
# Reports are saved on Windows for later before/after comparison.
$steps = @(
    @{ File = '01_setup.sql'; Report = '01_setup.txt' },
    @{ File = '02_baseline.sql'; Report = '02_baseline.txt' },
    @{ File = '03_add_index.sql'; Report = '03_add_index.txt' },
    @{ File = '04_after.sql'; Report = '04_after.txt' }
)

foreach ($step in $steps) {
    $source = Join-Path $sqlDir $step.File
    $destination = "/tmp/$($step.File)"
    Write-Host "`n=== $($step.File) ===" -ForegroundColor Cyan

    # docker cp copies the SQL file into the running PostgreSQL container.
    docker cp $source "${containerName}:$destination"
    if ($LASTEXITCODE -ne 0) { throw "Failed to copy: $($step.File)" }

    # -X: ignore .psqlrc  -v ON_ERROR_STOP=1: stop immediately on SQL errors
    # -f: run the SQL file stored inside the container.
    # PostgreSQL NOTICE messages are written to stderr even for successful SQL.
    # Do not merge stderr (2>&1) into the PowerShell pipeline: Windows PowerShell 5.1
    # can treat such a NOTICE as a terminating NativeCommandError when Stop is set.
    # Actual SQL failures are still detected by psql ON_ERROR_STOP and LASTEXITCODE.
    docker exec $containerName psql -X -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $destination |
        Tee-Object -FilePath (Join-Path $reportsDir $step.Report)

    if ($LASTEXITCODE -ne 0) { throw "Benchmark failed at $($step.File). Review logs." }
}

Write-Host "`nBenchmark completed. Reports: $reportsDir" -ForegroundColor Green
Write-Host 'Compare Execution Time, shared hit/read buffers, and scan operators across baseline vs after.'
Write-Host "Production database 'signalbridge' was not used in this benchmark."
