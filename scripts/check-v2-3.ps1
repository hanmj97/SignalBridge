# SignalBridge V2-3 read-only smoke checks. Does not create rules or send Discord messages.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host '[1/3] Verify protected API rejects anonymous access' -ForegroundColor Cyan
$url = 'http://localhost:8081/api/v1/opportunity-alerts'
try {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
    $status = [int]$response.StatusCode
} catch {
    if ($null -eq $_.Exception.Response) { throw "API is unreachable: $url" }
    $status = [int]$_.Exception.Response.StatusCode
}
if ($status -ne 401) { throw "Expected 401 on anonymous interest-rule access; got HTTP $status" }
Write-Host '[OK] Anonymous access blocked (401)'

Write-Host '[2/3] Verify source files exist' -ForegroundColor Cyan
$files = @(
    'db/migrations/016_opportunity_alerts.sql',
    'frontend/src/OpportunityAlertsPanel.tsx',
    'airflow/dags/opportunity_alert_notifier.py',
    'airflow/dags/signalbridge_opportunity_alerts.py'
)
foreach ($file in $files) { if (-not (Test-Path $file)) {throw "Missing $file"} }
Write-Host '[OK] Required source files present'

Write-Host '[3/3] Run safe Python matching tests' -ForegroundColor Cyan
py -3 -m unittest discover -s .\airflow\tests -p test_opportunity_alert_notifier.py -v
if ($LASTEXITCODE -ne 0) {throw 'Opportunity alert tests failed'}
Write-Host 'SignalBridge V2-3 non-destructive smoke tests PASSED.' -ForegroundColor Green
