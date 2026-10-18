# Non-destructive local check: no DB writes and no emails/webhooks are sent.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))

# Check for tracked credentials and broken .env formatting; values stay hidden.
py -3 '.\tools\phase15_preflight.py'
if ($LASTEXITCODE -ne 0) { throw 'Security preflight failed. Review FAIL lines before pushing.' }

# Test the preflight tool itself.
py -3 -m unittest discover -s '.\tools' -p 'test_*.py' -v
if ($LASTEXITCODE -ne 0) { throw 'Preflight regression tests failed' }

# Run existing Airflow and collector unit tests (no running DAG triggered).
py -3 -m unittest discover -s '.\airflow\tests' -v
if ($LASTEXITCODE -ne 0) { throw 'Airflow tests failed' }
py -3 -m unittest discover -s '.\collector\tests' -v
if ($LASTEXITCODE -ne 0) { throw 'Collector tests failed' }
Write-Host 'Phase 15 local checks passed. Build/test Java and React separately as documented.'
