# SignalBridge Phase 9: read-only API smoke test through the React dev proxy.
# Run from the project root after PostgreSQL migration and API rebuild.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:5173/api/v1'

# Keep cookies in one web session so the CSRF token belongs to that session.
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri "$base/auth/csrf" -WebSession $session
if ([string]::IsNullOrWhiteSpace($csrf.token)) {
    throw 'CSRF endpoint did not return a token.'
}
Write-Host 'CSRF endpoint OK'

# Unauthenticated users must not be able to read another person's watch rules.
try {
    Invoke-WebRequest -Uri "$base/watches" -WebSession $session -ErrorAction Stop | Out-Null
    throw 'SECURITY ERROR: anonymous watch endpoint returned success.'
} catch {
    $response = $_.Exception.Response
    if ($null -eq $response) { throw }
    $status = [int]$response.StatusCode
    if ($status -ne 401) {
        throw "Expected HTTP 401 for anonymous watch access, received HTTP $status."
    }
    Write-Host 'Anonymous watch access is blocked (HTTP 401)'
}
Write-Host 'Phase 9 read-only smoke checks passed.'
