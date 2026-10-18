# SignalBridge V2-5B — live Yongin API persistence and public catalog

Changes: lab DB migration 017, opt-in live importer, link-kind-aware REST projection + UI, safe opportunity alert gate. Original accounts/GitHub/Discord settings remain unchanged.

## Important source constraint
The official Yongin jobs API supplies ID, title, department, start/end dates, application flag, but NO guaranteed individual posting permalink. The app saves a **verified API dataset catalogue URL**, labels it as such, and NEVER pretends it is the original application link. This API does not prove applicant's eligibility. Fields not explicitly provided remain NULL/UNKNOWN. The date field semantic should be checked against the source guide before production release.

## Application order
1. Back up using the existing `scripts/phase15-backup.ps1`, plus lab backup if it contains valuable data.
2. Back up files before unpacking zip (UI/API/alert notifier).
3. Extract patch to project folder.
4. Apply `db/migrations/017_opportunity_link_kind.sql` to **signalbridge_v2_lab**, not signalbridge.
5. Run `scripts/check-v2-5b.ps1` (offline).
6. With `YONGIN_API_SERVICE_KEY` in local .env, run `py -3 -m collector.yongin_v25b --live --permit-network` for preview.
7. When satisfied, run `py -3 -m collector.yongin_v25b --live --permit-network --write-lab`. This writes real API items to lab only; run same command twice to confirm idempotency.
8. Rebuild API container and `web` frontend, verify `http://localhost:5173`.

## Behaviors / limitations
- Main `signalbridge` DB remains unchanged.
- `is_demo=false` for genuine API result; `link_kind='DATASET'` prevents labeling source listing as an article URL.
- Yongin API jobs are not necessarily 20–30 youth-specific or in Yongin itself: region/age are unknown unless provider supplies them.
- V2-3 opportunity notifier excludes `DATASET` link rows, so no personal Discord message is sent for records with no verified article link. Airflow is NOT modified to enable or schedule API ingestion.
- Only up to 20 items/page, pages 1–10 available with the existing safe API preflight client's bounded request parameters; additional pages and robust pagination are a future task.
- Windows DB connection relies on previously working local collector `.env` settings.
- If source API returns 0 rows in selected window, nothing is written; use `--start YYYY-MM-DD --end YYYY-MM-DD` within 60 days.
- For first write if V2-3 opportunity alerts DAG has been manually unpaused, pause it before migration; after migration dataset-only Yongin rows are excluded by notifier filter.
