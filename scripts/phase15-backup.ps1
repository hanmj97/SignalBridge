param(
    # Store sensitive backups outside the Git project by default.
    [string]$DestinationDirectory = (Join-Path $HOME 'SignalBridge-PrivateBackups')
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\', '/')
$dest = [System.IO.Path]::GetFullPath($DestinationDirectory).TrimEnd('\', '/')
if ($dest -eq $project -or $dest.StartsWith($project + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a backup directory OUTSIDE the project/Git repository.'
}
New-Item -Path $dest -ItemType Directory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$inside = '/tmp/signalbridge-p15-' + $timestamp + '.dump'
$out = Join-Path $dest ('signalbridge-' + $timestamp + '.dump')
try {
    # Create PostgreSQL custom-format backup in the DB container.
    & docker exec signalbridge-postgres pg_dump -U signalbridge -d signalbridge -Fc -f $inside
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }

    # Validate archive TOC without restoring or touching live tables.
    & docker exec signalbridge-postgres pg_restore --list $inside | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore archive listing failed' }

    # Copy validated archive to a private location outside the source tree.
    & docker cp ("signalbridge-postgres:" + $inside) $out
    if ($LASTEXITCODE -ne 0) { throw 'docker cp failed' }
    if ((Get-Item $out).Length -le 0) { throw 'Backup was empty' }
    Write-Host ('Backup verified: ' + $out)
    Write-Host 'This archive contains user data. Keep it private; do not upload to GitHub.'
} finally {
    # Remove only the specific temporary container file created above.
    & docker exec signalbridge-postgres rm -f $inside | Out-Null
}
