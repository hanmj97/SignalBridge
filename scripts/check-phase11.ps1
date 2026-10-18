# Phase 11 read-only smoke test. No Discord message is sent.
# Run from D:\Projects\SignalBridge in Windows PowerShell.
param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'

# Anonymous member Discord settings must remain private (HTTP 401).
try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/watches/discord" -UseBasicParsing
  throw "Unexpected HTTP $([int]$response.StatusCode) for anonymous Discord settings"
} catch {
  if ($null -eq $_.Exception.Response) { throw }
  $status = [int]$_.Exception.Response.StatusCode
  if ($status -ne 401) { throw "Expected HTTP 401, got $status" }
  Write-Host 'Anonymous Discord settings correctly blocked (HTTP 401)' -ForegroundColor Green
}

# Public CSRF endpoint must remain functional for authenticated requests.
$csrf = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/csrf" -SessionVariable session
if ([string]::IsNullOrWhiteSpace($csrf.token)) { throw 'CSRF token missing' }
Write-Host 'CSRF endpoint OK' -ForegroundColor Green
Write-Host 'Phase 11 read-only API smoke check passed.' -ForegroundColor Green
