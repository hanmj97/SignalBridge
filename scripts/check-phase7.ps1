# Phase 7: read-only API response smoke test. Does not change PostgreSQL rows.
param(
    [string]$ApiBase = 'http://localhost:8081'
)
$ErrorActionPreference = 'Stop'

# -TimeoutSec prevents the check from hanging if the API container is not responding.
$endpoint = "$($ApiBase.TrimEnd('/'))/api/v1/operations/ingestions"
Write-Host "Checking $endpoint" -ForegroundColor Cyan
$snapshot = Invoke-RestMethod -Uri $endpoint -TimeoutSec 20

# Basic structural and value checks, including empty-run scenarios.
if ($null -eq $snapshot.generatedAt) { throw 'generatedAt is missing' }
if ($snapshot.activeSourceCount -ne @($snapshot.sources).Count) { throw 'activeSourceCount does not match sources array' }
if ($snapshot.runsLast24Hours -lt 0 -or $snapshot.completedLast24Hours -lt 0 -or $snapshot.failuresLast24Hours -lt 0) {
    throw 'Ingestion metrics must not be negative'
}
if (($snapshot.completedLast24Hours + $snapshot.failuresLast24Hours) -gt $snapshot.runsLast24Hours) {
    throw 'Completed + failed counts exceed total executions'
}
if (@($snapshot.recentRuns).Count -gt 12) { throw 'Recent runs are not bounded to 12 rows' }
$allowedStatuses = @('RUNNING', 'SUCCESS', 'FAILED', 'NOT_MODIFIED')
foreach ($run in @($snapshot.recentRuns)) {
    if ($run.status -notin $allowedStatuses) { throw "Unexpected run status: $($run.status)" }
    if ($run.PSObject.Properties.Name -contains 'errorMessage') { throw 'Raw error messages must not be exposed' }
}

Write-Host 'Phase 7 API OK' -ForegroundColor Green
Write-Host "Sources: $($snapshot.activeSourceCount)"
Write-Host "Last 24 hours: $($snapshot.runsLast24Hours) runs, $($snapshot.completedLast24Hours) completed, $($snapshot.failuresLast24Hours) failed"
Write-Host "Latest 12 max: $(@($snapshot.recentRuns).Count) rows"
