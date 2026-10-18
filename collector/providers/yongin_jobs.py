"""Yongin City jobs and exams API safe, read-only validation adapter.

Official documentation: https://www.data.go.kr/data/15097328/openapi.do
Request: GET https://apis.data.go.kr/4050000/eplymtexam/getEplymtexam
Response does not guarantee an original article URL. The V2-5B importer stores
only a clearly labeled dataset link in the isolated LAB DB. Never print keys.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
import json
import re

ENDPOINT = 'https://apis.data.go.kr/4050000/eplymtexam/getEplymtexam'
MAX_BYTES = 1_000_000
ALLOWED_TYPES = frozenset({'채용정보', '시험 정보', '시험정보'})


class ProviderError(ValueError):
    """Exception whose message is guaranteed to exclude API keys and raw response."""


@dataclass(frozen=True)
class JobPreview:
    source_id: str
    title: str
    department: str | None
    starts_on: str | None
    ends_on: str | None
    source_status: str | None
    source_type: str
    # The published API specification does not guarantee a verified article URL.
    ready_for_persistence: bool = False


def _string(value, *, limit=300):
    if value is None:
        return None
    if not isinstance(value, (str, int)):
        raise ProviderError('unexpected field type')
    value = str(value).strip()
    if len(value) > limit:
        raise ProviderError('field exceeds length limit')
    return value or None


def _date(value):
    text = _string(value, limit=30)
    if text is None:
        return None
    try:
        if re.fullmatch(r'\d{8}', text):
            return date(int(text[:4]), int(text[4:6]), int(text[6:])).isoformat()
        return date.fromisoformat(text).isoformat()
    except ValueError:
        raise ProviderError('invalid published date') from None


def _extract_items(doc):
    if not isinstance(doc, dict):
        raise ProviderError('expected JSON object')
    response = doc.get('response', doc)
    if not isinstance(response, dict):
        raise ProviderError('invalid response envelope')
    header = response.get('header', {})
    if isinstance(header, dict):
        status = _string(header.get('resultCode'), limit=20)
        if status is not None and status not in ('0', '00'):
            raise ProviderError('API returned a non-success result code')
    body = response.get('body', response)
    if not isinstance(body, dict):
        raise ProviderError('invalid response body')
    items = body.get('items', body.get('item', []))
    if isinstance(items, dict):
        items = items.get('item', [])
    if items is None:
        return []
    if isinstance(items, dict):
        items = [items]
    if not isinstance(items, list) or len(items) > 1000:
        raise ProviderError('invalid item list')
    return items


def preview(doc):
    """Parse only documented columns; reject unknown envelope, no DB writes."""
    items = _extract_items(doc)
    output = []
    seen = set()
    for item in items:
        if not isinstance(item, dict):
            raise ProviderError('invalid item')
        source_type = _string(item.get('type'), limit=60)
        if source_type not in ALLOWED_TYPES:
            # E.g., 합격자발표 is not a current recruitment opportunity.
            continue
        source_id = _string(item.get('no'), limit=80)
        title = _string(item.get('sj'), limit=300)
        if not source_id or not title:
            raise ProviderError('missing article ID or title')
        if source_id in seen:
            raise ProviderError('duplicate article ID in one page')
        seen.add(source_id)
        start, end = _date(item.get('bgng_ymd')), _date(item.get('end_ymd'))
        if start and end and start > end:
            raise ProviderError('start date is after deadline')
        output.append(JobPreview(
            source_id=source_id, title=title,
            department=_string(item.get('dept_nm'),limit=100),
            starts_on=start, ends_on=end,
            source_status=_string(item.get('rcpt_yn'),limit=10),
            source_type=source_type,
        ))
    return output


def fetch_live(api_key: str, *, page=1, rows=5, start=None, end=None) -> dict:
    """Explicit GET. Never log URLs, body, request objects, or exceptions."""
    if not api_key or len(api_key) > 500:
        raise ProviderError('YONGIN_API_SERVICE_KEY is required')
    if not 1 <= page <= 10 or not 1 <= rows <= 20:
        raise ProviderError('page/rows outside limited inspection range')
    if start is None and end is None:
        end_date = date.today()
        start_date = end_date - timedelta(days=30)
    elif start is not None and end is not None:
        try:
            start_date, end_date = date.fromisoformat(start), date.fromisoformat(end)
        except ValueError:
            raise ProviderError('dates must be YYYY-MM-DD') from None
    else:
        raise ProviderError('provide both start and end dates')
    if (end_date-start_date).days < 0 or (end_date-start_date).days > 60:
        raise ProviderError('inspection date range must be 0-60 days')
    import requests
    params = {
        'serviceKey': api_key, 'pageNo': page, 'numOfRows': rows,
        # Official optional filter: request recruitment, not exams or result notices.
        # Still independently validate record types before database writes.
        'type': '채용정보',
        'srch_bgn_dt': start_date.strftime('%Y%m%d'),
        'srch_end_dt': end_date.strftime('%Y%m%d'),
    }
    try:
        r = requests.get(ENDPOINT,params=params,timeout=(5,15),allow_redirects=False,stream=True,
                         headers={'Accept':'application/json', 'User-Agent':'SignalBridge-V2-5-Preflight/1.0'})
        try:
            if r.status_code != 200:
                raise ProviderError(f'API returned HTTP {r.status_code}; response and key hidden')
            payload = bytearray()
            for chunk in r.iter_content(chunk_size=65536):
                payload.extend(chunk)
                if len(payload) > MAX_BYTES:
                    raise ProviderError('API JSON response exceeds size limit')
            try:
                doc = json.loads(payload)
            except (ValueError, UnicodeError):
                raise ProviderError('API did not return valid JSON') from None
            if not isinstance(doc,dict):
                raise ProviderError('unexpected JSON envelope')
            return doc
        finally:
            r.close()
    except requests.RequestException:
        raise ProviderError('API transport error; details hidden to protect key') from None
