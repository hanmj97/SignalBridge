"""V2-1 synthetic fixture importer; live Youth Center adapter is NOT enabled.

Dry run, no DB access: py -3 -m collector.opportunity_import --fixture collector/fixtures/opportunities_demo.json
Write to LAB DB only:  py -3 -m collector.opportunity_import --fixture ... --write-demo
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from collector.opportunity_logic import normalize_opportunity

LAB_DB = "signalbridge_v2_lab"
SOURCE_KEY = "v2_demo_opportunities"
SOURCE_PROVIDER = "demo_fixture"
SOURCE_RESOURCE = "synthetic_examples_not_official"


def load_fixture(path: Path) -> list[dict]:
    raw = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(raw, list) or not raw:
        raise ValueError("fixture must be a nonempty array")
    if len(raw) > 100:
        raise ValueError("demo fixture may contain at most 100 items")
    normalized = [normalize_opportunity(item, is_demo=True) for item in raw]
    keys = [item["external_id"] for item in normalized]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate external_id in fixture")
    return normalized


def write_fixture(items: list[dict]) -> tuple[int, int, int]:
    # Deferred import means --fixture dry run needs no psycopg or DB connection.
    from collector.main import connect
    from collector.opportunity_store import save_opportunity

    # Force a separate lab DB, never the existing signalbridge production DB.
    # Keep this process-local and restore the previous environment in finally.
    previous_db = os.environ.get("POSTGRES_DB")
    os.environ["POSTGRES_DB"] = LAB_DB
    try:
        return _write_into_lab(connect, save_opportunity, items)
    finally:
        if previous_db is None:
            os.environ.pop("POSTGRES_DB", None)
        else:
            os.environ["POSTGRES_DB"] = previous_db


def _write_into_lab(connect, save_opportunity, items: list[dict]) -> tuple[int, int, int]:
    with connect() as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute(
                    """INSERT INTO sources (source_key, label, provider, resource)
                       VALUES (%s, 'SignalBridge V2 - 가상 데모 자료', %s, %s)
                       ON CONFLICT (source_key) DO NOTHING""",
                    (SOURCE_KEY, SOURCE_PROVIDER, SOURCE_RESOURCE),
                )
                cur.execute(
                    "SELECT id, provider, resource FROM sources WHERE source_key=%s FOR UPDATE",
                    (SOURCE_KEY,),
                )
                row = cur.fetchone()
                if not row or (row[1], row[2]) != (SOURCE_PROVIDER, SOURCE_RESOURCE):
                    raise ValueError("demo source key already belongs to a different provider")
                source_id = row[0]
                cur.execute(
                    "INSERT INTO ingestion_runs (source_id) VALUES (%s) RETURNING id",
                    (source_id,),
                )
                run_id = cur.fetchone()[0]

        # Intentional separate transaction: a failed batch rolls back all record
        # writes; the failure can still be recorded in ingestion_runs.
        try:
            with conn.transaction():
                new_count = updated_count = unchanged_count = 0
                with conn.cursor() as cur:
                    for item in items:
                        result = save_opportunity(cur, source_id, item)
                        if result == "NEW":
                            new_count += 1
                        elif result == "UPDATED":
                            updated_count += 1
                        else:
                            unchanged_count += 1
                    cur.execute(
                        """UPDATE ingestion_runs
                           SET status='SUCCESS', finished_at=now(), fetched_count=%s,
                               new_count=%s, updated_count=%s WHERE id=%s""",
                        (len(items), new_count, updated_count, run_id),
                    )
                    cur.execute(
                        "UPDATE sources SET last_checked_at=now() WHERE id=%s",
                        (source_id,),
                    )
            return new_count, updated_count, unchanged_count
        except Exception as exc:
            with conn.transaction():
                with conn.cursor() as cur:
                    cur.execute(
                        """UPDATE ingestion_runs SET status='FAILED', finished_at=now(),
                               error_message=%s WHERE id=%s""",
                        (str(exc)[:1000], run_id),
                    )
            raise


def main() -> int:
    parser = argparse.ArgumentParser(description="SignalBridge V2-1 synthetic demo loader")
    parser.add_argument("--fixture", required=True, type=Path, help="JSON fixture path")
    parser.add_argument("--write-demo", action="store_true", help="Write DEMO rows to isolated signalbridge_v2_lab DB only")
    args = parser.parse_args()
    try:
        items = load_fixture(args.fixture)
        print(f"Validated {len(items)} SYNTHETIC rows (not real policies/jobs).")
        if not args.write_demo:
            print("DRY RUN: PostgreSQL was not accessed; add --write-demo to insert demo rows.")
            for item in items:
                print(f"  {item['external_id']}: {item['title']}")
            return 0
        new, updated, unchanged = write_fixture(items)
        print(f"DEMO LAB DB ({LAB_DB}) INSERT OK: new={new}, updated={updated}, unchanged={unchanged}")
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(f"Invalid fixture: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        # Do not dump connection strings, secrets, or API credentials.
        print(f"DB import failed: {type(exc).__name__}. Check schema and DB logs.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
