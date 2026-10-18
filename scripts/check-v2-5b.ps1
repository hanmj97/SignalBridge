# Run V2-5B offline tests; never calls live API or writes the DB.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    py -3 -m unittest collector.tests.test_yongin_v25b -v
    if ($LASTEXITCODE -ne 0) { throw 'V2-5B Python unit tests failed' }
    py -3 -m compileall -q collector\yongin_v25b.py
    if ($LASTEXITCODE -ne 0) { throw 'V2-5B syntax test failed' }
    Write-Host 'SignalBridge V2-5B offline checks PASSED' -ForegroundColor Green
} finally { Pop-Location }
