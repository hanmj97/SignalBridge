# Engineering V2-9: per-member GitHub Releases tracking + sorting hotfix

- Repairs V2-8 HTTP 500 cause: `...source_idLEFT JOIN` malformed SQL due to missing whitespace.
- Sorts cross-repository release/change lists by **GitHub release published_at** (not first discovery time).
  When a specific repository is selected, numeric semantic-version components sort first (Grafana 13.2.4 before 13.1.x/12.x), then publication time.
  Pre-release semantic-version precedence is not fully implemented. This is not sorting application code commits.
- Adds authenticated per-user repository registration, list and disconnect for public GitHub repositories.
- Uses one shared `sources` row per repository, deduplicated where applicable; registering a repository does not publish/change a GitHub release.
- Login owners see only their registered repositories in Records/Changes lists. The public dashboard and sources list display built-ins, not new user-added repositories. Registered repository URLs are visible to their subscribers only in list APIs.
- Existing watchlist can target subscribed sources through the member's source selector. Does not automatically enable Discord alerts: user must still create a watch rule.
- Existing VS Code / Docker / Grafana seeds and Phase 8 Discord watch rules remain untouched.
- New registered repositories are ingested on next run of `signalbridge_collector_30m`; unsubscribed custom repositories are skipped, data remains stored.

## Prerequisites
- Engineering V2-8 files applied. Pre-existing edits backed up.
- Source files are based on V2-8 and V2-7B applied. Do not overwrite more recent manual edits without comparing diffs.
- Database backup and `018_member_github_repositories.sql` to `signalbridge` only; never to the V2 lab DB.

## Validation
1. Apply migration; check table exists.
2. Build backend; test `/api/v1/records?page=0&size=10` no HTTP 500. Check error logs if any.
3. Build frontend; sign in, visit Engineering > 내 GitHub 저장소.
4. Add `https://github.com/grafana/grafana`, inspect saved list, remove and confirm only your association disappears.
5. Trigger collector manually or Airflow DAG (for custom repos), then verify user's release list and change history.
6. Filter on Grafana; check 13.2.4 before 13.1.x / 12.x **if those versions are present**.
7. No existing DB, Discord or V2 youth/job data reset.

Important: Github Releases are releases published to GitHub, not arbitrary repository commits or pull request changes. Private repositories need a separate OAuth/GitHub App permission flow, not supported yet.
