# SignalBridge V2-5A offline tests. No live API requests, DB writes, or Discord.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# Verify registry and documented-response mock parser.
py -3 -m unittest collector.tests.test_provider_v25 -v
if ($LASTEXITCODE -ne 0) { throw 'V2-5 unit tests FAILED' }

# Check that provider registry lists all planned sources.
py -3 -m collector.provider_v25 --list
if ($LASTEXITCODE -ne 0) { throw 'V2-5 registry check FAILED' }

# Parse only a clearly labelled synthetic response.
py -3 -m collector.provider_v25 --fixture-yongin '.\collector\fixtures\yongin_synthetic_v25.json'
if ($LASTEXITCODE -ne 0) { throw 'V2-5 synthetic payload check FAILED' }

Write-Host 'SignalBridge V2-5A OFFLINE checks PASSED (no DB writes / no network calls).' -ForegroundColor Green
