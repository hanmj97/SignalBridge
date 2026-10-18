# V2-2 non-destructive HTTP smoke test. Requires API container restarted
# with docker-compose.v2-2.yml and existing V2 lab populated.
param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'

Write-Host 'SignalBridge V2-2 API smoke checks (read-only)'

# Public API returns a paged catalog. includeDemo=true is explicit.
$page = Invoke-RestMethod -Uri "$BaseUrl/api/v1/opportunities?includeDemo=true&size=12" -Method Get
if ($page.totalElements -lt 3) {
    throw 'Expected at least 3 synthetic V2-1 rows in signalbridge_v2_lab.'
}
if (-not $page.items -or $page.items.Count -eq 0) {
    throw 'No items returned from the opportunity API.'
}
if (@($page.items | Where-Object { $_.isDemo -ne $true }).Count -gt 0) {
    throw 'Unexpected non-demo item returned from V2 lab.'
}
Write-Host "[OK] Opportunity list: $($page.totalElements) records"

# Every synthetic item must be hidden by default from the public non-demo request.
$public = Invoke-RestMethod -Uri "$BaseUrl/api/v1/opportunities?size=12" -Method Get
if ($public.totalElements -ne 0) {
    throw 'Expected no real public records in the isolated demo lab.'
}
Write-Host '[OK] Demo rows excluded by default'

# Detail and change history should work, not just the list view.
$id = $page.items[0].id
$detail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/opportunities/$id`?includeDemo=true" -Method Get
if ($detail.item.id -ne $id -or -not $detail.history -or $detail.history.Count -lt 1) {
    throw 'Opportunity detail or change history is missing.'
}
Write-Host '[OK] Opportunity detail and version history'

# Test filtering without making any data changes.
$jobs = Invoke-RestMethod -Uri "$BaseUrl/api/v1/opportunities?kind=JOB&includeDemo=true" -Method Get
if (@($jobs.items | Where-Object { $_.kind -ne 'JOB' }).Count -gt 0) {
    throw 'Kind filter returned unexpected records.'
}
Write-Host '[OK] Kind filter'

Write-Host 'SignalBridge V2-2 smoke checks PASSED (read only).' -ForegroundColor Green
