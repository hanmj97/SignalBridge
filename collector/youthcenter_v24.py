"""V2-4 Youth Center integration PRE-FLIGHT.

Synthetic feed parser & strictly gated live envelope inspection. This module
DOES NOT map unverified official response fields or write real API data.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

from collector.opportunity_logic import normalize_opportunity

# The request URL and parameters below come from the public Youth Center API
# documentation example; approval and a real response are required to validate.
OFFICIAL_ENDPOINT = 'https://www.youthcenter.go.kr/opi/youthPlcyList.do'
MAX_BYTES = 2_000_000
MAX_ROWS = 100
SYNTHETIC_ROOT = 'SignalBridgeSyntheticFeed'


class FeedError(ValueError):
    """Safe error whose contents never include credentials / provider response."""


def safe_parse_xml(raw: bytes) -> ET.Element:
    if not isinstance(raw, bytes) or not raw or len(raw) > MAX_BYTES:
        raise FeedError('XML missing or outside permitted size')
    # xml.etree should not process external entities; fail closed on DOCTYPE.
    if re.search(rb'<!\s*(?:DOCTYPE|ENTITY)\b', raw, re.I):
        raise FeedError('DTD and XML entities are not accepted')
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        raise FeedError('XML is malformed') from None


def _only_text(item: ET.Element, tag: str) -> str | None:
    children = item.findall(tag)
    if len(children) > 1:
        raise FeedError('duplicate XML field')
    if not children or children[0].text is None:
        return None
    return children[0].text.strip() or None


def parse_synthetic_feed(raw: bytes) -> list[dict]:
    """Only our own tagged fake fixture. NEVER use for official XML."""
    root = safe_parse_xml(raw)
    if root.tag != SYNTHETIC_ROOT or root.get('synthetic') != 'true' or root.get('version') != '1':
        raise FeedError('not a SignalBridge synthetic v1 fixture')
    items = root.findall('item')
    if len(items) < 1 or len(items) > MAX_ROWS or len(root) != len(items):
        raise FeedError('invalid synthetic item count/structure')
    out = []
    for item in items:
        def optional_int(tag: str):
            value = _only_text(item, tag)
            if value is None: return None
            if not value.isascii() or not value.isdecimal():
                raise FeedError('age is not an integer')
            return int(value)
        regions = []
        for group in item.findall('regions'):
            regions.extend((entry.text or '').strip() for entry in group.findall('region'))
        candidate = {
            'external_id': _only_text(item,'external_id'),
            'title': _only_text(item,'title'),
            'url': _only_text(item,'url'),
            'kind': _only_text(item,'kind'),
            'organization': _only_text(item,'organization'),
            'category': _only_text(item,'category'),
            'region_codes': regions,
            'application_start': _only_text(item,'application_start'),
            'application_deadline': _only_text(item,'application_deadline'),
            'status': _only_text(item,'status') or 'UNKNOWN',
            'age_min': optional_int('age_min'),
            'age_max': optional_int('age_max'),
            'employment_type': _only_text(item,'employment_type'),
            'eligibility_summary': _only_text(item,'eligibility_summary'),
            'benefit_summary': _only_text(item,'benefit_summary'),
            'source_published_at': _only_text(item,'source_published_at'),
        }
        try:
            out.append(normalize_opportunity(candidate, is_demo=True))
        except (TypeError, ValueError) as exc:
            # Error field names are safe; never output the item values.
            raise FeedError(f'synthetic item failed validation: {type(exc).__name__}') from None
    ids = [v['external_id'] for v in out]
    if len(set(ids)) != len(ids):
        raise FeedError('duplicate source IDs in a single feed')
    return out


def inspect_official_xml(raw: bytes) -> dict:
    """Provider response envelope inspection: NO field mapping or DB writes."""
    root = safe_parse_xml(raw)
    def localname(s: str) -> str:
        return s.rsplit('}',1)[-1].split(':')[-1][:90]
    # Only metadata about XML tags. NEVER show values: could include tokens.
    top_level = sorted({localname(c.tag) for c in list(root)[:200]})
    return {'root': localname(root.tag), 'top_level_tags': top_level[:25], 'payload_bytes': len(raw)}


def fetch_official_xml(api_key: str, *, page: int = 1, display: int = 10) -> bytes:
    """Explicit network operation, never used by the default CLI or Airflow DAG."""
    import requests
    if not api_key or not (1 <= page <= 10) or not (1 <= display <= 50):
        raise FeedError('missing key or page/display outside safe preflight limits')
    # Fixed official HTTPS host; do not follow redirects with credentialed URL.
    try:
        response = requests.get(
            OFFICIAL_ENDPOINT,
            params={'openApiVlak': api_key, 'pageIndex': page, 'display': display},
            headers={'User-Agent':'SignalBridge-Development-Preflight/0.1', 'Accept':'application/xml,text/xml'},
            timeout=(5, 20), allow_redirects=False, stream=True,
        )
        try:
            if response.status_code != 200:
                raise FeedError(f'official endpoint returned HTTP {response.status_code} (no response body logged)')
            body = bytearray()
            for chunk in response.iter_content(chunk_size=65536):
                body.extend(chunk)
                if len(body) > MAX_BYTES:
                    raise FeedError('official XML response too large')
            raw = bytes(body)
            safe_parse_xml(raw)
            return raw
        finally:
            response.close()
    except requests.RequestException:
        # requests exceptions can include full URL/query containing the API key.
        raise FeedError('official API network request failed (credentials hidden)') from None


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description='SignalBridge Youth Center V2-4 offline preflight')
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--fixture', type=Path, help='Parse a labelled synthetic XML fixture')
    mode.add_argument('--inspect-live', action='store_true', help='Explicit network call; no DB writes')
    p.add_argument('--write-demo', action='store_true', help='Fixture only; lab DB only')
    p.add_argument('--permit-network', action='store_true', help='Required with --inspect-live')
    args = p.parse_args(argv)
    try:
        if args.fixture is not None:
            if args.permit_network:
                raise FeedError('--permit-network is only valid with --inspect-live')
            items = parse_synthetic_feed(args.fixture.read_bytes())
            print(f'V2-4 synthetic XML verified: {len(items)} DEMO records (not official data)')
            if args.write_demo:
                # V2-1 writer guarantees signalbridge_v2_lab and demo-source only.
                from collector.opportunity_import import write_fixture
                added, changed, same = write_fixture(items)
                print(f'LAB DB demo write OK: new={added}, updated={changed}, unchanged={same}')
            else:
                print('OFFLINE DRY RUN: no DB writes, no network calls, no Discord alerts')
            return 0
        if args.write_demo or not args.permit_network:
            raise FeedError('live inspect needs --permit-network and never allows --write-demo')
        # dotenv loaded only after an explicitly opted-in network request.
        from dotenv import load_dotenv
        load_dotenv(Path(__file__).resolve().parents[1]/'.env')
        key = os.environ.get('YOUTHCENTER_API_KEY', '').strip()
        if not key:
            raise FeedError('YOUTHCENTER_API_KEY is missing')
        inspection = inspect_official_xml(fetch_official_xml(key))
        print('LIVE XML envelope inspected. NO personal or policy data stored.')
        print('XML root:', inspection['root'])
        print('Direct child tags:', ', '.join(inspection['top_level_tags']))
        print('Payload size (bytes):', inspection['payload_bytes'])
        print('Official field mapping remains DISABLED pending validation.')
        return 0
    except (OSError, FeedError) as exc:
        # The errors produced here never include the secret URL/query or data.
        print(f'V2-4 preflight failed: {exc}',file=sys.stderr)
        return 1
    except Exception as exc:
        # Eg. PostgreSQL unavailable, never dump potentially sensitive DSNs.
        print(f'V2-4 processing failed: {type(exc).__name__} (details hidden)',file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
