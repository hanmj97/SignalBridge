"""Process authenticated GitHub refresh requests queued by the web API.

Airflow calls this periodically. It reuses the existing GitHub collector and
never accepts arbitrary URLs/commands from a browser request.
"""
from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone

from collector.main import connect, run_source

MAX_REQUESTS = 10


def claim_requests(conn, limit: int = MAX_REQUESTS) -> list[tuple[int, int]]:
    """Claim a bounded batch without keeping locks during external API calls."""
    if not 1 <= limit <= MAX_REQUESTS:
        raise ValueError("limit must be between 1 and 10")
    with conn.transaction():
        with conn.cursor() as cur:
            # Recover a task interrupted by a container restart.
            cur.execute("""
                UPDATE github_refresh_requests
                SET status='PENDING', started_at=NULL
                WHERE status='RUNNING'
                  AND started_at < now() - interval '20 minutes'
            """)
            cur.execute("""
                SELECT id, source_id FROM github_refresh_requests
                WHERE status='PENDING'
                ORDER BY requested_at, id
                LIMIT %s FOR UPDATE SKIP LOCKED
            """, (limit,))
            rows = cur.fetchall()
            if rows:
                cur.execute("""
                    UPDATE github_refresh_requests
                    SET status='RUNNING', started_at=now(), finished_at=NULL
                    WHERE id = ANY(%s)
                """, ([item[0] for item in rows],))
    return rows


def finish_requests(conn, request_ids: list[int], success: bool) -> None:
    """Complete all requests for the same fetched repository together."""
    if not request_ids:
        return
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE github_refresh_requests
                SET status=%s, finished_at=now()
                WHERE id = ANY(%s) AND status='RUNNING'
            """, ("SUCCESS" if success else "FAILED", request_ids))


def run_pending(limit: int = MAX_REQUESTS) -> dict[str, int]:
    """Poll queue and fetch each distinct subscribed GitHub repository once."""
    with connect() as conn:
        pending = claim_requests(conn, limit)
        grouped: dict[int, list[int]] = defaultdict(list)
        for request_id, source_id in pending:
            grouped[source_id].append(request_id)

        succeeded = failed = 0
        for source_id, ids in grouped.items():
            good = False
            try:
                with conn.cursor() as cur:
                    cur.execute("""
                        SELECT s.id, s.label, s.resource, s.etag
                        FROM sources s
                        WHERE s.id=%s AND s.enabled=TRUE AND s.provider='github_releases'
                          AND EXISTS (
                            SELECT 1 FROM user_repository_subscriptions u
                            WHERE u.source_id=s.id
                          )
                    """, (source_id,))
                    source = cur.fetchone()
                if source:
                    good = run_source(conn, source)
            except Exception:
                # Avoid printing exception text: upstream errors may include credentials.
                good = False
            finally:
                finish_requests(conn, ids, good)
            succeeded += len(ids) if good else 0
            failed += 0 if good else len(ids)
        return {"claimed": len(pending), "succeeded": succeeded, "failed": failed}


if __name__ == "__main__":
    print(run_pending())
