# V2-5C offline tests: no live API, DB writes, or notification delivery.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # Provider / import contract tests (uses mocks only)
    py -3 -m unittest collector.tests.test_yongin_v25b collector.tests.test_yongin_scheduler_v25c -v
    if ($LASTEXITCODE -ne 0) { throw 'V2-5C Python unit tests failed' }
    # DAG parsing syntax (does not require Windows to have Airflow installed)
    py -3 -m py_compile '.\airflow\dags\signalbridge_yongin_live.py' '.\collector\yongin_scheduler_v25c.py'
    if ($LASTEXITCODE -ne 0) { throw 'V2-5C syntax check failed' }
    Write-Host 'SignalBridge V2-5C OFFLINE checks PASSED (no network / DB writes)' -ForegroundColor Green
} finally { Pop-Location }
