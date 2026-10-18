# Phase 13: safe, no-email, no-account-write smoke checks.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:5173/api/v1/auth'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri "$base/csrf" -WebSession $session
if (-not $csrf.token) { throw 'CSRF token missing' }
Write-Host 'CSRF OK'
$headers = @{ 'X-CSRF-TOKEN' = $csrf.token }

# Deliberately malformed email: must not send email; returns generic 202.
$reply = Invoke-WebRequest -UseBasicParsing -Uri "$base/password-reset/request" `
    -WebSession $session -Method Post -ContentType 'application/json' `
    -Headers $headers -Body '{"email":"invalid-address"}'
if ([int]$reply.StatusCode -ne 202) { throw "Expected 202, got $($reply.StatusCode)" }
Write-Host 'Request returns generic HTTP 202 (no email sent)'

# Invalid token should fail before any DB write.
try {
    Invoke-WebRequest -UseBasicParsing -Uri "$base/password-reset/confirm" `
      -WebSession $session -Method Post -ContentType 'application/json' `
      -Headers $headers -Body '{"token":"invalid","newPassword":"NewPassword#2026"}' `
      -ErrorAction Stop | Out-Null
    throw 'ERROR: malformed token accepted'
} catch {
    if ($_.Exception.Message -like 'ERROR:*') { throw }
    if ($null -eq $_.Exception.Response) { throw }
    if ([int]$_.Exception.Response.StatusCode -ne 400) {
        throw "Expected 400, got $([int]$_.Exception.Response.StatusCode)"
    }
    Write-Host 'Malformed reset token rejected (HTTP 400)'
}
Write-Host 'Phase 13 password recovery smoke checks passed. No email sent.'
