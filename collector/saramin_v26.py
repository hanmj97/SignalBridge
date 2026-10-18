"""Explicitly opt-in Saramin preflight, offline by default, always read-only.

DO NOT deploy as a scheduled crawler or publish data without reviewing granted
API usage rights and the official conditions of service.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys

from collector.providers.saramin_jobs import SaraminError, fetch_live, parse_jobs


FIXTURE = Path(__file__).resolve().parent / "fixtures" / "saramin_synthetic_v26.json"


def run(argv=None) -> int:
    p = argparse.ArgumentParser(description="SignalBridge V2-6A Saramin PREVIEW (no database writes)")
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--fixture", action="store_true", help="Read synthetic JSON sample")
    mode.add_argument("--live", action="store_true", help="Read from official API with approved key")
    p.add_argument("--permit-network", action="store_true", help="Required for live calls")
    p.add_argument("--keywords", default="개발자", help="Search term; live only")
    p.add_argument("--count", type=int, default=10, help="1-20 records per request")
    args = p.parse_args(argv)
    if args.fixture and args.permit_network:
        p.error("fixture mode must not permit network")
    if args.live and not args.permit_network:
        p.error("live mode requires --permit-network")
    try:
        if args.fixture:
            import json
            document = json.loads(FIXTURE.read_text(encoding="utf-8"))
            rows = parse_jobs(document)
            print(f"SYNTHETIC offline preview: {len(rows)} fake job records")
        else:
            from dotenv import load_dotenv
            load_dotenv(Path(__file__).resolve().parents[1] / ".env")
            key = os.getenv("SARAMIN_ACCESS_KEY", "").strip()
            if not key:
                raise SaraminError("approved SARAMIN_ACCESS_KEY not configured")
            rows = parse_jobs(fetch_live(key, keywords=args.keywords, count=args.count))
            print(f"Official Saramin read-only preview: {len(rows)} records")
        for item in rows:
            # No raw API body, auth key, or URL query strings on stdout.
            print(f"  ID={item['external_id']} | {item['organization']} | {item['title']}")
        print("No DB writes. No Airflow schedule. No Discord notifications.")
        return 0
    except (SaraminError, ValueError, UnicodeError, OSError):
        print("Saramin preflight failed: check provider response/credentials/fixture. Sensitive details hidden.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(run())
