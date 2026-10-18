# Phase 10: endpoint/status smoke check, creates no accounts and sends no email.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:5173/api/v1/auth'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Read CSRF token from a single session.
$csrf = Invoke-RestMethod -Uri "$base/csrf" -WebSession $session
if (-not $csrf.token) { throw 'No CSRF token from backend.' }
Write-Host 'CSRF endpoint OK'

# Without any token, verifying an account must be rejected.
try {
    Invoke-RestMethod -Uri "$base/verify-email" -WebSession $session -Method Post -ContentType 'application/json' -Headers @{ 'X-CSRF-TOKEN' = $csrf.token } -Body '{"token":"bad"}' -ErrorAction Stop | Out-Null
    throw 'ERROR: malformed verification token was accepted.'
} catch {
    if ($_.Exception.Message -like 'ERROR:*') { throw }
    $response = $_.Exception.Response
    if ($null -eq $response) { throw }
    if ([int]$response.StatusCode -ne 400) { throw "Expected HTTP 400, got $([int]$response.StatusCode)" }
    Write-Host 'Malformed token rejected (HTTP 400)'
}

# Confirm anonymous /me returns 401, without leaking private data.
try {
    Invoke-RestMethod -Uri "$base/me" -WebSession $session -ErrorAction Stop | Out-Null
    throw 'ERROR: anonymous user obtained a member profile.'
} catch {
    if ($_.Exception.Message -like 'ERROR:*') { throw }
    if ($null -eq $_.Exception.Response) { throw }
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw 'Expected HTTP 401' }
    Write-Host 'Anonymous profile access blocked (HTTP 401)'
}
Write-Host 'Phase 10 email API smoke checks passed (no email sent).'
