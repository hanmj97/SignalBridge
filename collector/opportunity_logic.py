"""Provider-neutral opportunity normalization.

The JSON fixtures in V2-1 are SYNTHETIC. This module defines the canonical
contract for later adapters, not the current Youth Center XML field mapping.
Do not infer eligibility, status, or publication dates from missing data.
"""
from __future__ import annotations

from datetime import date, datetime
from urllib.parse import urlparse

from collector.logic import content_hash, changed_fields  # Existing hash/diff contract

KINDS = frozenset({"POLICY", "BENEFIT", "JOB", "PROGRAM"})
STATUSES = frozenset({"OPEN", "UPCOMING", "CLOSED", "UNKNOWN"})


def _optional_text(item: dict, key: str, limit: int = 4000) -> str | None:
    value = item.get(key)
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        raise ValueError(f"{key} must be text")
    result = value.strip()
    if not result:
        return None
    if len(result) > limit:
        raise ValueError(f"{key} is too long")
    return result


def _required_text(item: dict, key: str, limit: int = 500) -> str:
    value = _optional_text(item, key, limit)
    if value is None:
        raise ValueError(f"{key} is required")
    return value


def _iso_date(item: dict, key: str) -> str | None:
    value = _optional_text(item, key, 20)
    if value is None:
        return None
    parsed = date.fromisoformat(value)
    if parsed.isoformat() != value:
        raise ValueError(f"{key} must be YYYY-MM-DD")
    return value


def _iso_datetime(item: dict, key: str) -> str | None:
    value = _optional_text(item, key, 40)
    if value is None:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError(f"{key} must include a timezone")
    return parsed.isoformat(timespec="seconds")


def _age(item: dict, key: str) -> int | None:
    value = item.get(key)
    if value is None:
        return None
    if type(value) is not int or not 0 <= value <= 120:
        raise ValueError(f"{key} must be an integer from 0 to 120")
    return value


def normalize_opportunity(item: dict, *, is_demo: bool) -> dict:
    """Create stable hashable data. Omit scraping timestamps and view counters."""
    if not isinstance(item, dict):
        raise ValueError("opportunity item must be an object")
    external_id = _required_text(item, "external_id", 200)
    title = _required_text(item, "title", 500)
    url = _required_text(item, "url", 2000)
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError("url must be an HTTPS URL without credentials")
    if any(char.isspace() for char in url):
        raise ValueError("url must not contain whitespace")
    kind = _required_text(item, "kind", 20).upper()
    status = (item.get("status") or "UNKNOWN")
    if not isinstance(status, str):
        raise ValueError("status must be text")
    status = status.upper()
    if kind not in KINDS:
        raise ValueError("invalid opportunity kind")
    if status not in STATUSES:
        raise ValueError("invalid opportunity status")
    regions = item.get("region_codes", [])
    if not isinstance(regions, list) or any(not isinstance(x, str) or not x.strip() for x in regions):
        raise ValueError("region_codes must be a list of strings")
    regions = sorted({code.strip() for code in regions})
    if len(regions) > 30 or any(len(code) > 40 for code in regions):
        raise ValueError("too many / too long region codes")
    start = _iso_date(item, "application_start")
    deadline = _iso_date(item, "application_deadline")
    if start and deadline and start > deadline:
        raise ValueError("application_start must not exceed application_deadline")
    age_min = _age(item, "age_min")
    age_max = _age(item, "age_max")
    if age_min is not None and age_max is not None and age_min > age_max:
        raise ValueError("age_min must not exceed age_max")
    return {
        "external_id": external_id,
        "title": title,
        "url": url,
        "kind": kind,
        "organization": _optional_text(item, "organization", 500),
        "category": _optional_text(item, "category", 100),
        "region_codes": regions,
        "application_start": start,
        "application_deadline": deadline,
        "status": status,
        "age_min": age_min,
        "age_max": age_max,
        "employment_type": _optional_text(item, "employment_type", 100),
        "eligibility_summary": _optional_text(item, "eligibility_summary"),
        "benefit_summary": _optional_text(item, "benefit_summary"),
        "source_published_at": _iso_datetime(item, "source_published_at"),
        "is_demo": bool(is_demo),
    }
