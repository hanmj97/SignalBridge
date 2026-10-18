# SignalBridge V2-1 non-destructive collector validation.
# This script does NOT write to PostgreSQL, send emails, or call external APIs.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))

# Validate all existing and new collector unit tests.
py -3 -m unittest discover -s '.\collector\tests' -p 'test_*.py' -v
if ($LASTEXITCODE -ne 0) { throw 'Collector unit tests failed' }

# Parse/normalize the synthetic JSON fixture without DB writes.
py -3 -m collector.opportunity_import --fixture '.\collector\fixtures\opportunities_demo.json'
if ($LASTEXITCODE -ne 0) { throw 'Fixture dry run failed' }

Write-Host 'SignalBridge V2-1 dry-run checks PASSED (no DB writes).' -ForegroundColor Green
