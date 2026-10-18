"""SignalBridge V2-6A: read-only Saramin official Job Search API adapter.

- Official spec: https://oapi.saramin.co.kr/guide/job-search
- Authorization is required; no automatic network calls and NO DB persistence.
- Do not print/log API keys, HTTP request URLs, raw responses, or stack traces.
- Normalize *only documented* fields. Omit unprovided eligibility conditions.
- No user-facing republication/retention until API license is confirmed.
"""
from __future__ import annotations

from datetime import datetime, timezone, timedelta
import json
import re
from urllib.parse import parse_qs, urlsplit, urlunsplit, urlencode

from collector.opportunity_logic import normalize_opportunity

ENDPOINT = "https://oapi.saramin.co.kr/job-search"
MAX_RESPONSE_BYTES = 1_500_000
SEOUL = timezone(timedelta(hours=9))
_ALLOWED_HOSTS = {"saramin.co.kr", "www.saramin.co.kr"}


class SaraminError(ValueError):
    """Safe failures which never contain raw API responses or secrets."""


def _text(value, field, maximum=500):
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (str, int)):
        raise SaraminError(f"invalid {field} type")
    value = str(value).strip()
    if len(value) > maximum:
        raise SaraminError(f"{field} too long")
    return value or None


def _name(value, field):
    if value is None:
        return None
    if isinstance(value, str):
        return _text(value, field)
    if not isinstance(value, dict):
        raise SaraminError(f"invalid {field} object")
    return _text(value.get("name"), field)


def _timestamp(value, field):
    if value is None or value == "":
        return None
    value = _text(value, field, 25)
    if not value or not re.fullmatch(r"\d{9,12}", value):
        raise SaraminError(f"invalid {field} timestamp")
    try:
        dt = datetime.fromtimestamp(int(value), tz=timezone.utc).astimezone(SEOUL)
    except (OverflowError, ValueError, OSError):
        raise SaraminError(f"{field} timestamp out of range") from None
    return dt


def _trusted_job_url(raw, external_id):
    raw = _text(raw, "url", 2500)
    if not raw:
        raise SaraminError("missing official job URL")
    try:
        parts = urlsplit(raw)
        port = parts.port
    except ValueError:
        raise SaraminError("invalid official job URL") from None
    if parts.scheme not in ("http", "https") or parts.hostname not in _ALLOWED_HOSTS:
        raise SaraminError("job URL is not from the official provider")
    if parts.username or parts.password or port not in (None, 80, 443):
        raise SaraminError("invalid official URL credentials/port")
    if parts.path != "/zf_user/jobs/relay/view":
        raise SaraminError("unexpected official job URL path")
    try:
        query = parse_qs(parts.query, keep_blank_values=True, strict_parsing=False)
    except ValueError:
        raise SaraminError("invalid official job URL query") from None
    if query.get("rec_idx") != [external_id]:
        raise SaraminError("official job URL ID mismatch")
    # Normalize verified official http links to HTTPS and remove volatile tracking params.
    return urlunsplit(("https", "www.saramin.co.kr", parts.path,
                       urlencode({"rec_idx": external_id}), ""))


def _region_names(location):
    name = _name(location, "location")
    if not name:
        return []
    # Normalize named administrative top-level areas only, without inferring eligibility.
    cities = []
    for part in name.split(","):
        city = part.strip().split(" > ", 1)[0].strip()
        if city and city not in cities:
            cities.append(city)
    return cities[:30]


def parse_jobs(document: dict) -> list[dict]:
    """Return normalized preview rows; zero network, database, or notification actions."""
    if not isinstance(document, dict):
        raise SaraminError("JSON object required")
    if "code" in document and "jobs" not in document:
        raise SaraminError("provider returned error response; code/body hidden")
    container = document.get("jobs")
    if not isinstance(container, dict):
        raise SaraminError("unexpected jobs response envelope")
    raw_jobs = container.get("job", [])
    if raw_jobs is None:
        raw_jobs = []
    if isinstance(raw_jobs, dict):
        raw_jobs = [raw_jobs]
    if not isinstance(raw_jobs, list) or len(raw_jobs) > 110:
        raise SaraminError("invalid number of jobs")
    result, seen = [], set()
    for entry in raw_jobs:
        if not isinstance(entry, dict):
            raise SaraminError("invalid job entry")
        external_id = _text(entry.get("id"), "id", 80)
        if not external_id or not external_id.isdecimal():
            raise SaraminError("missing/invalid job ID")
        if external_id in seen:
            raise SaraminError("duplicate job ID in one response")
        seen.add(external_id)
        company = entry.get("company")
        if not isinstance(company, dict):
            raise SaraminError("invalid/missing company")
        company_detail = company.get("detail", company)
        if not isinstance(company_detail, dict):
            raise SaraminError("invalid company detail")
        org = _name(company_detail.get("name"), "company")
        position = entry.get("position")
        if not isinstance(position, dict):
            raise SaraminError("invalid/missing position")
        title = _text(position.get("title"), "title", 500)
        if not title or not org:
            raise SaraminError("missing job title/company")
        active = _text(entry.get("active"), "active", 5)
        if active not in ("0", "1"):
            raise SaraminError("invalid active status")
        close_type = entry.get("close-type")
        if isinstance(close_type, dict):
            close_code = _text(close_type.get("code"), "close-type", 5)
        else:
            close_code = _text(close_type, "close-type", 5)
        opened = _timestamp(entry.get("opening-timestamp"), "opening")
        published = _timestamp(entry.get("posting-timestamp"), "posting")
        expires = _timestamp(entry.get("expiration-timestamp"), "expiration")
        # '채용시', '상시', '수시' do not claim a definite application deadline.
        deadline = expires.date().isoformat() if close_code == "1" and expires else None
        raw = {
            "external_id": external_id,
            "title": title,
            "url": _trusted_job_url(entry.get("url"), external_id),
            "kind": "JOB",
            "organization": org,
            "category": _name(position.get("job-mid-code"), "job-mid-code"),
            "region_codes": _region_names(position.get("location")),
            "application_start": opened.date().isoformat() if opened else None,
            "application_deadline": deadline,
            "status": "OPEN" if active == "1" else "CLOSED",
            "age_min": None,
            "age_max": None,
            "employment_type": _name(position.get("job-type"), "job-type"),
            "eligibility_summary": None,
            "benefit_summary": None,
            "source_published_at": published.isoformat(timespec="seconds") if published else None,
        }
        try:
            result.append(normalize_opportunity(raw, is_demo=False))
        except (ValueError, TypeError):
            raise SaraminError("provider row failed canonical validation") from None
    return result


def fetch_live(access_key: str, *, keywords="개발자", start=0, count=10):
    """Explicit, bounded, read-only request. Does not log key-bearing URL."""
    if not isinstance(access_key, str) or not 8 <= len(access_key.strip()) <= 500:
        raise SaraminError("approved SARAMIN_ACCESS_KEY required")
    if not isinstance(keywords, str) or not 1 <= len(keywords.strip()) <= 80:
        raise SaraminError("invalid keyword")
    if type(start) is not int or not 0 <= start <= 1000:
        raise SaraminError("start out of safe inspection range")
    if type(count) is not int or not 1 <= count <= 20:
        raise SaraminError("count out of safe inspection range")
    import requests
    response = None
    try:
        response = requests.get(
            ENDPOINT,
            params={"access-key": access_key, "keywords": keywords.strip(),
                    "start": start, "count": count, "sort": "pd"},
            headers={"Accept": "application/json", "User-Agent": "SignalBridge-V2-6A-Preflight/1.0"},
            timeout=(5, 20), allow_redirects=False, stream=True,
        )
        if response.status_code != 200:
            raise SaraminError(f"provider returned HTTP {response.status_code}; body hidden")
        payload = bytearray()
        for chunk in response.iter_content(chunk_size=65536):
            payload.extend(chunk)
            if len(payload) > MAX_RESPONSE_BYTES:
                raise SaraminError("provider response exceeds maximum size")
        try:
            document = json.loads(payload)
        except (ValueError, UnicodeError):
            raise SaraminError("provider returned invalid JSON") from None
        if not isinstance(document, dict):
            raise SaraminError("provider returned unexpected JSON envelope")
        return document
    except requests.RequestException:
        raise SaraminError("provider network error; details hidden") from None
    finally:
        if response is not None:
            response.close()
