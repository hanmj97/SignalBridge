# Phase 12 smoke check: READ-ONLY unauthenticated requests. Does not modify DB, email, or accounts.
$ErrorActionPreference='Stop'
$base='http://localhost:8081'

# PowerShell 5.1 invokes non-2xx responses as exceptions. Both cases are handled here.
function Get-HttpStatus([string] $url) {
    try {
        $response=Invoke-WebRequest -Uri $url -UseBasicParsing -ErrorAction Stop
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

# The public CSRF endpoint remains usable for login forms.
$csrf=Get-HttpStatus "$base/api/v1/auth/csrf"
if ($csrf -ne 200) { throw "CSRF endpoint expected 200, got $csrf" }
Write-Host 'CSRF endpoint OK'

# The new account endpoint must require a session, unlike public catalog GET endpoints.
$profile=Get-HttpStatus "$base/api/v1/account/profile"
if ($profile -ne 401) { throw "Anonymous account profile expected 401, got $profile" }
Write-Host 'Anonymous account profile blocked (HTTP 401)'
Write-Host 'Phase 12 read-only smoke check passed.'
