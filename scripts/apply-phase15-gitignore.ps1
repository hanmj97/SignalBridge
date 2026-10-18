# Append safety patterns ONLY. This does not re-encode or overwrite existing .gitignore contents.
# It also does not untrack files that were already committed.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $root '.gitignore'
$marker = '# BEGIN SignalBridge Phase 15 safety rules'
$block = @'
# BEGIN SignalBridge Phase 15 safety rules
# Local configuration / backups / release output must never be committed
.env
.env.*
!.env.example
**/.env
**/.env.*
!**/.env.example
backups/
**/backups/
_phase*_staging/
_phase*staging/
*.dump
*.backup
*.bak
*.sqlite
*.sqlite3
*.p12
*.pfx
*.pem
*.key
node_modules/
dist/
__pycache__/
.venv/
perf-lab/reports/
# END SignalBridge Phase 15 safety rules
'@
if (Test-Path -LiteralPath $target) {
    $exists = [System.IO.File]::ReadAllText($target).Contains($marker)
    if ($exists) {
        Write-Host 'Phase 15 ignore rules already present; no changes.'
        exit 0
    }
    $stream = [System.IO.File]::Open($target, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
    try {
        $needsNewline = $false
        if ($stream.Length -gt 0) {
            $stream.Seek(-1, [System.IO.SeekOrigin]::End) | Out-Null
            $last = $stream.ReadByte()
            $needsNewline = ($last -ne 10 -and $last -ne 13)
        }
        $stream.Seek(0, [System.IO.SeekOrigin]::End) | Out-Null
        $prefix = if ($needsNewline) { "`r`n" } else { '' }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($prefix + $block + "`r`n")
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
} else {
    # Create new file as UTF-8 without BOM; no existing data to overwrite.
    [System.IO.File]::WriteAllText($target, $block + "`r`n", (New-Object System.Text.UTF8Encoding($false)))
}
Write-Host 'Phase 15 ignore rules appended; review tracked files before pushing.'
