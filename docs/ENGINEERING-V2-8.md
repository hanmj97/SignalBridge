# SignalBridge Engineering V2-8 — latest changes first + monitor our own GitHub Releases

## Scope
- Only Engineering `records` / `changes` pages; the youth-policy/job catalog remains unchanged.
- Release list sorting: `record_versions.detected_at` from the latest version by version number, DESC and record ID DESC.
- `last_seen_at` is **NOT** suitable: it changes on identical re-polls.
- Change history sorting: event `detected_at DESC NULLS LAST, id DESC` (including per-release history).
- GitHub project source: registered as `github_signalbridge_self` in the operational `signalbridge` DB, NOT `signalbridge_v2_lab`.
- No schema migrations or DAG changes; existing `collector.main` and 30-minute Airflow DAG automatically pick up the new enabled `github_releases` source.

## What must be true
- Your local Git `origin` points at the actual SignalBridge GitHub repository; otherwise pass `--repo owner/repo`.
- The repository must be visible to the GitHub API with your `GITHUB_TOKEN` if private.
- The repository must contain **GitHub Releases** for rows to appear; normal commits and tags alone do not generate Release records.
- Do not create a Release from source ZIP files containing `.env`, credentials, private backups or data dumps.
- The registration script checks API access first. If verification fails, no source is registered, preventing the existing DAG from failing due to a wrong repository.

## Quick checks
```powershell
Set-Location 'D:\Projects\SignalBridge'
.\scripts\check-engineering-v28.ps1
py -3 -m collector.register_signalbridge_source
py -3 -m collector.register_signalbridge_source --apply
py -3 -m collector.main
```

If there is no local `origin`:
```powershell
py -3 -m collector.register_signalbridge_source --repo YOUR-OWNER/YOUR-REPO
py -3 -m collector.register_signalbridge_source --repo YOUR-OWNER/YOUR-REPO --apply
```

## Check SQL in the operational `signalbridge` database
```sql
SELECT source_key, label, provider, resource, enabled, last_checked_at
FROM sources WHERE source_key='github_signalbridge_self';

SELECT r.title, r.first_seen_at, r.last_seen_at,
       COALESCE((SELECT v.detected_at FROM record_versions v
                 WHERE v.record_id=r.id ORDER BY v.version DESC LIMIT 1), r.first_seen_at)
           AS latest_changed_at
FROM records r JOIN sources s ON s.id=r.source_id
WHERE s.provider='github_releases'
ORDER BY latest_changed_at DESC, r.id DESC LIMIT 20;

SELECT v.detected_at, r.title, v.change_type, v.version
FROM record_versions v JOIN records r ON r.id=v.record_id
JOIN sources s ON s.id=r.source_id
WHERE s.provider='github_releases'
ORDER BY v.detected_at DESC NULLS LAST, v.id DESC LIMIT 20;
```

## Rollback
- Restore the 4 original changed files from backup (`CatalogRepository.java`, `RecordSummary.java`, `App.tsx`, `api.ts`) and rebuild API/frontend.
- Optional reversible disable of only the new source:
  `UPDATE sources SET enabled=FALSE WHERE source_key='github_signalbridge_self';`
- The version records and existing sources are never deleted by this patch.
