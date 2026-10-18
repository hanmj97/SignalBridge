# SignalBridge V2-7A — safe contract checks; no DB/API/Discord access.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# V2-7A narrow two-category product-scope contract.
py -3 -m unittest collector.tests.test_v27a_service_scope -v
if ($LASTEXITCODE -ne 0) { throw 'V2-7A product scope tests failed' }

# Existing, offline API adapter regressions.
py -3 -m unittest collector.tests.test_provider_v25 collector.tests.test_saramin_v26 -q
if ($LASTEXITCODE -ne 0) { throw 'Existing provider regression tests failed' }

# Detect a syntax issue before rebuilding your backend/frontend containers.
py -3 -m py_compile collector/providers/registry.py collector/tests/test_v27a_service_scope.py
if ($LASTEXITCODE -ne 0) { throw 'V2-7A Python syntax check failed' }

Write-Host 'SignalBridge V2-7A OFFLINE checks PASSED' -ForegroundColor Green
Write-Host 'Then rebuild Spring API and React in your existing Docker environment.'
