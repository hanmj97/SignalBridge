"""V2-5C: bounded, opt-in ingestion of Yongin's official recruitment data.

Composes V2-5B provider validation/storage; does not change their behavior.
Use only with approved API key. Writes only to signalbridge_v2_lab.
Never sends Discord notifications. Does not delete absent records.
"""
from __future__ import annotations

import argparse
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from collector.providers.yongin_jobs import ProviderError, fetch_live, preview
from collector.yongin_v25b import LAB_DB, _write_in_lab, normalize_batch

MAX_PAGES = 10
MAX_ROWS = 20


@dataclass(frozen=True)
class RunSummary:
    pages_requested: int
    rows_collected: int
    inserted: int
    updated: int
    unchanged: int
    wrote_to_db: bool


def collect_once(
    key: str,
    *,
    pages: int = 3,
    rows: int = 20,
    start: str | None = None,
    end: str | None = None,
    persist: bool = False,
    fetch: Callable = fetch_live,
    parse: Callable = preview,
    write: Callable = _write_in_lab,
) -> RunSummary:
    """Fetch bounded pages; validate *entire* batch before any DB write.

    No deletion is attempted when a record disappears from a partial source page.
    Duplicate keys spanning API pages abort the entire batch to avoid page drift.
    """
    if not 1 <= pages <= MAX_PAGES:
        raise ValueError('pages must be 1..10')
    if not 1 <= rows <= MAX_ROWS:
        raise ValueError('rows must be 1..20')
    if not isinstance(key, str) or not key.strip() or len(key) > 500:
        raise ValueError('approved API key required')
    all_rows = []
    visited = set()
    requested = 0
    for page in range(1, pages + 1):
        document = fetch(key, page=page, rows=rows, start=start, end=end)
        parsed = parse(document)
        requested += 1
        if not isinstance(parsed, list):
            raise ProviderError('provider parser returned unexpected structure')
        # The API's page length includes non-recruitment entries which may be
        # filtered by preview(); do not interpret filtered empty pages as EOF.
        for item in parsed:
            item_id = getattr(item, 'source_id', None)
            if not item_id or item_id in visited:
                raise ProviderError('missing or repeated source ID across API pages')
            visited.add(item_id)
            all_rows.append(item)
    items = normalize_batch(all_rows)
    if not persist or not items:
        return RunSummary(requested, len(items), 0, 0, 0, False)
    n, u, same = write(items)
    return RunSummary(requested, len(items), n, u, same, True)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description='Bounded Yongin API live importer (V2 LAB only)')
    parser.add_argument('--live', action='store_true', help='Explicitly use the official API')
    parser.add_argument('--permit-network', action='store_true', help='Explicitly permit network access')
    parser.add_argument('--write-lab', action='store_true', help='Opt in to LAB DB writes')
    parser.add_argument('--pages', type=int, default=3, help='Pages requested, 1..10')
    parser.add_argument('--rows', type=int, default=20, help='Rows per page, 1..20')
    parser.add_argument('--start', help='YYYY-MM-DD, both start and end required')
    parser.add_argument('--end', help='YYYY-MM-DD, both start and end required')
    args = parser.parse_args(argv)
    if not args.live or not args.permit_network:
        print('Live network call denied; provide --live --permit-network', file=sys.stderr)
        return 2
    try:
        from dotenv import load_dotenv
        load_dotenv(Path(__file__).resolve().parents[1] / '.env')
        key = os.environ.get('YONGIN_API_SERVICE_KEY', '').strip()
        if not key:
            print('YONGIN_API_SERVICE_KEY is not configured', file=sys.stderr)
            return 2
        result = collect_once(key, pages=args.pages, rows=args.rows, start=args.start,
                              end=args.end, persist=args.write_lab)
        print(f'Yongin V2-5C: pages={result.pages_requested}, records={result.rows_collected}')
        if result.wrote_to_db:
            print(f'LAB DB ({LAB_DB}) new={result.inserted}, updated={result.updated}, unchanged={result.unchanged}')
        elif args.write_lab:
            print('No eligible records in requested pages; no database writes')
        else:
            print('READ ONLY preview; supply --write-lab after verification')
        print('Original article links are not present in this API; saved links are labeled DATASET.')
        return 0
    except (ProviderError, ValueError):
        print('Import aborted: provider response, credentials, or argument validation failed; sensitive details hidden', file=sys.stderr)
        return 1
    except Exception as exc:
        print(f'Import aborted ({type(exc).__name__}); inspect schema, LAB DB, and Airflow setup',file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
