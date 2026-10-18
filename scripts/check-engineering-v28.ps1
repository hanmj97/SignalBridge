# Offline tests: no calls to GitHub, PostgreSQL or Discord.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

py -3 -m unittest -v collector.tests.test_register_signalbridge_source
if ($LASTEXITCODE -ne 0) { throw 'GitHub source registration tests failed' }

# Static checks for the release sort implementation.
$repository = Get-Content '.\backend\src\main\java\dev\signalbridge\api\repository\CatalogRepository.java' -Raw -Encoding UTF8
if ($repository -notmatch 'LEFT JOIN LATERAL' -or $repository -notmatch 'COALESCE\(last_change.detected_at, r.first_seen_at\) DESC') {
    throw 'Latest-change ordering SQL missing'
}
if ($repository -notmatch 'ORDER BY v.detected_at DESC NULLS LAST, v.id DESC') {
    throw 'Change history ordering SQL missing'
}
Write-Host 'SignalBridge Engineering V2-8 OFFLINE checks PASSED'
