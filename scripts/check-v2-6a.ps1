# Run SignalBridge V2-6A read-only Saramin tests. No API key or DB required.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

# Python unit tests for official JSON envelope, validation, links, limits, and opt-in.
py -3 -m unittest collector.tests.test_saramin_v26 -v
if ($LASTEXITCODE -ne 0) { throw 'V2-6A Saramin unit tests failed' }

# Render only synthetic data and avoid any network, DB writes, or notifications.
py -3 -m collector.saramin_v26 --fixture
if ($LASTEXITCODE -ne 0) { throw 'V2-6A Saramin fixture inspection failed' }

Write-Host 'SignalBridge V2-6A Saramin OFFLINE checks PASSED' -ForegroundColor Green
