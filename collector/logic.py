"""Pure transformation functions for easy unit testing."""
import hashlib
import json


def normalize_release(item: dict) -> dict:
    """Keep meaningful, relatively stable release fields (exclude counters/asset metadata)."""
    return {
        "title": item.get("name") or item.get("tag_name") or "Untitled release",
        "tag_name": item.get("tag_name") or "",
        "url": item.get("html_url") or "",
        "published_at": item.get("published_at"),
        "body": item.get("body") or "",
        "prerelease": bool(item.get("prerelease")),
        "draft": bool(item.get("draft")),
    }


def content_hash(payload: dict) -> str:
    canonical = json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def changed_fields(previous: dict, current: dict) -> list[str]:
    return sorted(key for key in set(previous) | set(current) if previous.get(key) != current.get(key))
