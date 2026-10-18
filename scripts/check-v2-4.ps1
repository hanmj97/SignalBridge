# SignalBridge V2-4 local OFFLINE smoke tests.
# This script makes no network requests and performs no DB writes.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# New parser and HTTP safety unit tests (requests must be installed locally).
py -3 -m unittest collector.tests.test_youthcenter_v24 -v
if ($LASTEXITCODE -ne 0) { throw 'V2-4 unit tests failed' }

# Verify Python CLI fixture loading without writing to PostgreSQL.
py -3 -m collector.youthcenter_v24 `
  --fixture '.\collector\fixtures\youthcenter_synthetic_v24.xml'
if ($LASTEXITCODE -ne 0) { throw 'V2-4 synthetic XML verification failed' }

# Check Airflow file syntax without importing Airflow on Windows.
py -3 -c "import ast,pathlib; p=pathlib.Path('airflow/dags/signalbridge_youthcenter_preflight.py');ast.parse(p.read_text(encoding='utf-8'));print('Airflow DAG Python syntax OK')"
if ($LASTEXITCODE -ne 0) { throw 'V2-4 DAG syntax failed' }

Write-Host 'SignalBridge V2-4 OFFLINE checks PASSED (no DB writes / network requests).' -ForegroundColor Green
