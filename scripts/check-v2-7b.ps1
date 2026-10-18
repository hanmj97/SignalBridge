# V2-7B offline-only regression suite: no DB writes, API calls, or Discord.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    # Check approved Yongin-only rules and previous two-category UI guard.
    py -3 -m unittest collector.tests.test_v27b_yongin_jobs_only collector.tests.test_v27a_service_scope -v
    if ($LASTEXITCODE -ne 0) { throw 'V2-7B scope tests failed' }

    # Check existing read-only provider and DB importer logic using mock data.
    py -3 -m unittest collector.tests.test_provider_v25 collector.tests.test_yongin_v25b collector.tests.test_yongin_scheduler_v25c -q
    if ($LASTEXITCODE -ne 0) { throw 'Yongin regression tests failed' }

    # Check Python source syntax independently of installed Airflow.
    py -3 -m py_compile collector/providers/yongin_jobs.py collector/yongin_v25b.py collector/tests/test_v27b_yongin_jobs_only.py
    if ($LASTEXITCODE -ne 0) { throw 'V2-7B Python syntax check failed' }

    Write-Host 'SignalBridge V2-7B recruitment-only OFFLINE checks PASSED' -ForegroundColor Green
} finally {
    Pop-Location
}
