# Engineering V2-10 — GitHub refresh button + scheduled collection

## Behavior

- The existing `signalbridge_collector_30m` Airflow DAG already checks all
  enabled GitHub sources every **30 minutes**. It requires the Airflow container
  to be running, and the DAG must be unpaused.
- A signed-in member can request a refresh of a repository they registered via
  `POST /api/v1/my-repositories/{sourceId}/refresh`. The endpoint **only
  enqueues** a DB job: it does not run Python or fetch arbitrary user URLs.
- The new `signalbridge_github_manual_refresh` DAG polls the queue every
  minute, reuses `collector.main.run_source`, and marks job outcomes.
- The browser polls authenticated `GET /api/v1/my-repositories/{sourceId}/refresh`
  to display PENDING / RUNNING / SUCCESS / FAILED.
- The queue endpoint uses the existing Spring session and CSRF middleware;
  only the current member's subscribed and enabled `github_releases` source
  IDs are accepted.
- Repeated clicks from the same account on the same repository within 2 minutes
  return the existing job. Simultaneous in-flight duplicates are blocked by
  a partial unique DB index.
- This is **near-real-time, not instantaneous**; normal delay is around one
  minute plus GitHub API response time, subject to Airflow health/availability.
- New releases detected by the manual DAG are persisted. Existing
  `signalbridge_collector_30m` continues regular alert matching; manual DAG
  does not send Discord messages directly.

## Prerequisites

- Local project has already applied Engineering V2-9 and 9.1.
- `signalbridge` DB, Spring Boot API, Airflow and React are operational.
- Do not paste API keys, passwords or the Discord webhook into chats or Git.

## Apply

1. Back up the `signalbridge` PostgreSQL DB using the existing safe backup
   script, and preserve uncommitted local changes.
2. Apply `db/migrations/019_github_refresh_requests.sql` to **signalbridge only**,
   not `signalbridge_v2_lab`.
3. Run Maven, Python and frontend tests; rebuild the API (new controller).
4. The new Airflow DAG is paused on creation. Verify imports and manually
   unpause `signalbridge_github_manual_refresh` only after step 2.
5. Keep `signalbridge_collector_30m` enabled for periodic 30-min collection.
6. Open Engineering -> 내 GitHub 저장소, register a public repo, press
   지금 최신화, and watch the refresh status.
7. Verify a matching `records` row and the latest
   `github_refresh_requests.status=SUCCESS`.

## Verifications

SQL (run in DBeaver against the `signalbridge` database):

```sql
SELECT s.resource, q.status, q.requested_at, q.started_at, q.finished_at
FROM github_refresh_requests q
JOIN sources s ON s.id=q.source_id
ORDER BY q.id DESC LIMIT 20;

SELECT s.resource, r.title, r.payload->>'tag_name' AS tag_name
FROM records r JOIN sources s ON s.id=r.source_id
WHERE lower(s.resource)='hanmj97/signalbridge'
ORDER BY r.id DESC LIMIT 10;
```

If a request stays PENDING: check whether the new Airflow DAG is enabled.
If it becomes FAILED: check Airflow task logs and `ingestion_runs` for that
source. If automatic 30-minute collection is inactive: ensure the original
DAG is unpaused, Airflow is running, and the GitHub token has capacity.
No database content or prior subscriptions should be deleted to retry.
