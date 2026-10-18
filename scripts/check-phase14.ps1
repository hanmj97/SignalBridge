# Phase 14 non-destructive checks. NEVER deletes an account or sends a real email.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'

# CSRF endpoint should return a token and establish a browser-like session.
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri "$base/api/v1/auth/csrf" -WebSession $session
if (-not $csrf.token) { throw 'CSRF token was not provided.' }
Write-Host 'CSRF endpoint OK'

function Assert-Status {
    param([string]$Path,[string]$Method,[string]$Json,[int]$Expected)
    try {
        $null = Invoke-WebRequest -Uri "$base$Path" -Method $Method -WebSession $session `
            -Headers @{'X-CSRF-TOKEN'=$csrf.token} -ContentType 'application/json' -Body $Json -UseBasicParsing
        $status = 200
    }
    catch {
        $status = [int]$_.Exception.Response.StatusCode
    }
    if ($status -ne $Expected) { throw "Expected $Expected for $Path; got $status" }
    Write-Host "$Method $Path : HTTP $status"
}

# Anonymous requests cannot modify account email or delete an account.
Assert-Status -Path '/api/v1/account/email/request' -Method 'POST' `
    -Json '{"newEmail":"test@example.com","currentPassword":"dummy"}' -Expected 401
Assert-Status -Path '/api/v1/account/profile' -Method 'DELETE' `
    -Json '{"currentPassword":"dummy","confirmation":"DELETE"}' -Expected 401

# Invalid link must not touch any account.
Assert-Status -Path '/api/v1/auth/email-change/confirm' -Method 'POST' `
    -Json '{"token":"bad-token"}' -Expected 400
Write-Host 'Phase 14 non-destructive API checks passed.'
