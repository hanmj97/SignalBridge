"""V2-5A provider discovery and safe, explicit network inspection. No DB writes."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import sys

from collector.providers.registry import PROVIDERS
from collector.providers.yongin_jobs import ProviderError, preview, fetch_live


def main(argv=None):
    p=argparse.ArgumentParser(description='SignalBridge V2-5A provider inspection (READ ONLY)')
    modes=p.add_mutually_exclusive_group(required=True)
    modes.add_argument('--list',action='store_true',help='Show provider list and access type')
    modes.add_argument('--fixture-yongin',type=Path,metavar='PATH',help='Parse synthetic JSON')
    modes.add_argument('--live-yongin',action='store_true',help='Call official Yongin API: explicit opt-in')
    p.add_argument('--permit-network',action='store_true',help='Required with live API calls')
    p.add_argument('--start',help='Optional YYYY-MM-DD start date (with --end)')
    p.add_argument('--end',help='Optional YYYY-MM-DD end date (with --start)')
    try:
        args=p.parse_args(argv)
        if args.list:
            if args.permit_network or args.start or args.end:
                raise ProviderError('network/date flags are invalid with --list')
            for spec in PROVIDERS:
                print(f'{spec.code:18} | {spec.access:32} | {spec.label}')
            print('Provider listed != API access granted. All adapters default to no network and no DB writes.')
            return 0
        if args.fixture_yongin:
            if args.permit_network or args.start or args.end:
                raise ProviderError('network/date flags are invalid with --fixture-yongin')
            data=json.loads(args.fixture_yongin.read_text(encoding='utf-8-sig'))
            out=preview(data)
            print(f'Yongin SYNTHETIC preview: {len(out)} recruitment rows; no DB writes')
        else:
            if not args.permit_network:
                raise ProviderError('--live-yongin requires --permit-network')
            from dotenv import load_dotenv
            load_dotenv(Path(__file__).resolve().parents[1] / '.env')
            key=os.getenv('YONGIN_API_SERVICE_KEY','').strip()
            if not key:
                raise ProviderError('YONGIN_API_SERVICE_KEY is not configured in .env')
            out=preview(fetch_live(key,start=args.start,end=args.end))
            print(f'LIVE Yongin response parsed: {len(out)} recruitment rows; no DB writes')
        for row in out[:5]:
            print(f'  {row.source_id}: {row.title[:100]} / {row.starts_on or "?"} ~ {row.ends_on or "?"}')
        print('Persist disabled: official API response does not guarantee per-article source URLs.')
        return 0
    except ProviderError as exc:
        # ProviderError messages are controlled by our adapter and never include credentials.
        print(f'V2-5 provider inspection failed: {exc}', file=sys.stderr)
        return 1
    except (OSError, ValueError):
        # Never echo raw parsing errors or HTTP URLs.
        print('V2-5 provider inspection failed: verify fixture and API response format', file=sys.stderr)
        return 1
    except Exception as exc:
        print(f'V2-5 provider inspection failed: {type(exc).__name__} (details hidden)',file=sys.stderr)
        return 1


if __name__=='__main__':
    raise SystemExit(main())
