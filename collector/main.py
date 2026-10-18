"""Collect public GitHub releases and version meaningful changes in PostgreSQL.

Run: python -m collector.main
Needs: local PostgreSQL started with docker compose and dependencies installed.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import psycopg
from psycopg.types.json import Jsonb
import requests
from dotenv import load_dotenv

from collector.logic import changed_fields, content_hash, normalize_release

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")


def connect():
    return psycopg.connect(
        host=os.getenv("SIGNALBRIDGE_DB_HOST", "127.0.0.1"),
        port=int(os.getenv("SIGNALBRIDGE_DB_PORT", "5434")),
        dbname=os.getenv("POSTGRES_DB", "signalbridge"),
        user=os.getenv("POSTGRES_USER", "signalbridge"),
        password=os.getenv("POSTGRES_PASSWORD", ""),
        connect_timeout=10,
        autocommit=True,  # Explicit conn.transaction() blocks commit independently.
    )


def fetch_releases(resource: str, etag: str | None):
    # Resource always comes from our local source registry, not from user input.
    owner, repo = resource.split("/", 1)
    url = f"https://api.github.com/repos/{owner}/{repo}/releases"
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "SignalBridge-Portfolio-MVP/0.1",
    }
    if etag:
        headers["If-None-Match"] = etag
    token = os.getenv("GITHUB_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    response = requests.get(url, headers=headers, params={"per_page": 20}, timeout=20)
    if response.status_code == 304:
        return None, etag
    response.raise_for_status()
    body = response.json()
    if not isinstance(body, list):
        raise ValueError(f"Expected release list from {url}")
    return body, response.headers.get("ETag")


def save_record(cur, source_id: int, item: dict) -> str:
    external_id = str(item["id"])
    normalized = normalize_release(item)
    digest = content_hash(normalized)
    title, url = normalized["title"], normalized["url"]
    if not url:
        raise ValueError(f"Missing URL for release {external_id}")

    # Unique constraint prevents duplicate records across retries.
    cur.execute(
        """
        INSERT INTO records (source_id, external_id, title, url, payload, content_hash)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_id, external_id) DO NOTHING
        RETURNING id
        """,
        (source_id, external_id, title, url, Jsonb(normalized), digest),
    )
    row = cur.fetchone()
    if row is not None:
        record_id = row[0]
        cur.execute(
            """
            INSERT INTO record_versions
              (record_id, version, change_type, changed_fields, content_hash, payload)
            VALUES (%s, 1, 'NEW', %s, %s, %s)
            """,
            (record_id, list(normalized), digest, Jsonb(normalized)),
        )
        return "NEW"

    # Row lock keeps version increments consistent with concurrent collectors.
    cur.execute(
        """
        SELECT id, payload, content_hash, current_version
        FROM records WHERE source_id = %s AND external_id = %s FOR UPDATE
        """,
        (source_id, external_id),
    )
    record_id, old_payload, old_hash, current_version = cur.fetchone()
    if old_hash == digest:
        cur.execute("UPDATE records SET last_seen_at = now() WHERE id = %s", (record_id,))
        return "UNCHANGED"
    new_version = current_version + 1
    field_diff = changed_fields(old_payload, normalized)
    cur.execute(
        """
        UPDATE records SET title=%s, url=%s, payload=%s, content_hash=%s,
                           current_version=%s, last_seen_at=now() WHERE id=%s
        """,
        (title, url, Jsonb(normalized), digest, new_version, record_id),
    )
    cur.execute(
        """
        INSERT INTO record_versions
           (record_id, version, change_type, changed_fields, content_hash, payload)
        VALUES (%s, %s, 'UPDATED', %s, %s, %s)
        """,
        (record_id, new_version, field_diff, digest, Jsonb(normalized)),
    )
    return "UPDATED"


def run_source(conn, source) -> bool:
    source_id, label, resource, etag = source
    # Create the ingestion-run row in its own committed transaction.
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("INSERT INTO ingestion_runs (source_id) VALUES (%s) RETURNING id", (source_id,))
            run_id = cur.fetchone()[0]
    print(f"[START] {label} ({resource})")
    try:
        releases, next_etag = fetch_releases(resource, etag)
        with conn.transaction():
            with conn.cursor() as cur:
                if releases is None:
                    cur.execute(
                        "UPDATE ingestion_runs SET status='NOT_MODIFIED', finished_at=now() WHERE id=%s",
                        (run_id,),
                    )
                    cur.execute("UPDATE sources SET last_checked_at=now() WHERE id=%s", (source_id,))
                    print(f"[NOT MODIFIED] {label}")
                    return True

                added = updated = 0
                for item in releases:
                    if item.get("id") is None:
                        raise ValueError("GitHub release missing id")
                    status = save_record(cur, source_id, item)
                    if status == "NEW":
                        added += 1
                    elif status == "UPDATED":
                        updated += 1
                cur.execute(
                    """
                    UPDATE ingestion_runs SET status='SUCCESS', finished_at=now(),
                        fetched_count=%s, new_count=%s, updated_count=%s WHERE id=%s
                    """,
                    (len(releases), added, updated, run_id),
                )
                cur.execute(
                    "UPDATE sources SET etag=%s, last_checked_at=now() WHERE id=%s",
                    (next_etag, source_id),
                )
                print(f"[OK] {label}: fetched={len(releases)}, new={added}, updated={updated}")
                return True
    except (requests.RequestException, ValueError, psycopg.Error) as exc:
        # The record writes have been rolled back, but run failure is logged separately.
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE ingestion_runs SET status='FAILED', finished_at=now(),
                        error_message=%s WHERE id=%s
                    """,
                    (str(exc)[:1000], run_id),
                )
        print(f"[ERROR] {label}: {exc}", file=sys.stderr)
        return False


def main():
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """SELECT s.id, s.label, s.resource, s.etag FROM sources s
                WHERE s.enabled = TRUE AND s.provider='github_releases'
                  AND (NOT starts_with(s.source_key, 'github_user_')
                       OR EXISTS (SELECT 1 FROM user_repository_subscriptions u WHERE u.source_id=s.id))
                ORDER BY s.id"""
            )
            sources = cur.fetchall()
        if not sources:
            print("No enabled GitHub sources found")
            return 1
        errors = 0
        for source in sources:
            if not run_source(conn, source):
                errors += 1
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
