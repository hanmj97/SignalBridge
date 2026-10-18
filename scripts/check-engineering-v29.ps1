# Safe local preflight; never prints .env or modifies database.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
# Verify Python collector still imports and has no syntax errors.
py -3 -m compileall -q collector
if ($LASTEXITCODE -ne 0) { throw 'Python compile failed' }
# Run existing Python tests with no GitHub API calls.
py -3 -m unittest discover -s collector/tests -p 'test_*.py' -q
if ($LASTEXITCODE -ne 0) { throw 'Python tests failed' }
Write-Host 'Engineering V2-9 Python checks PASSED. Continue with API/React builds.'
