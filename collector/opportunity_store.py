"""Transactional, idempotent record/version upsert with V2 detail index.

Existing GitHub collector is NOT modified. A future approved provider adapter
may call save_opportunity with provider-verified, canonical data.
"""
from __future__ import annotations

from psycopg.types.json import Jsonb
from collector.logic import changed_fields, content_hash

DETAIL_COLUMNS = (
    "kind", "organization", "category", "region_codes", "application_start",
    "application_deadline", "status", "age_min", "age_max", "employment_type",
    "eligibility_summary", "benefit_summary", "source_published_at", "is_demo"
)


def _save_details(cur, record_id: int, item: dict) -> None:
    # Column identifiers are hardcoded from source, never from external data.
    cur.execute(
        """
        INSERT INTO opportunity_details (
          record_id, kind, organization, category, region_codes,
          application_start, application_deadline, status, age_min, age_max,
          employment_type, eligibility_summary, benefit_summary,
          source_published_at, is_demo
        ) VALUES (
          %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        ) ON CONFLICT (record_id) DO UPDATE SET
          kind=EXCLUDED.kind, organization=EXCLUDED.organization,
          category=EXCLUDED.category, region_codes=EXCLUDED.region_codes,
          application_start=EXCLUDED.application_start,
          application_deadline=EXCLUDED.application_deadline,
          status=EXCLUDED.status, age_min=EXCLUDED.age_min,
          age_max=EXCLUDED.age_max, employment_type=EXCLUDED.employment_type,
          eligibility_summary=EXCLUDED.eligibility_summary,
          benefit_summary=EXCLUDED.benefit_summary,
          source_published_at=EXCLUDED.source_published_at,
          is_demo=EXCLUDED.is_demo, indexed_at=now()
        """,
        (record_id,) + tuple(item[key] for key in DETAIL_COLUMNS),
    )


def save_opportunity(cur, source_id: int, item: dict) -> str:
    """Must be called inside an explicit DB transaction."""
    digest = content_hash(item)
    ext_id, title, url = item["external_id"], item["title"], item["url"]
    cur.execute(
        """
        INSERT INTO records (source_id, external_id, title, url, payload, content_hash)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_id, external_id) DO NOTHING RETURNING id
        """,
        (source_id, ext_id, title, url, Jsonb(item), digest),
    )
    inserted = cur.fetchone()
    if inserted:
        record_id = inserted[0]
        cur.execute(
            """
            INSERT INTO record_versions
                (record_id, version, change_type, changed_fields, content_hash, payload)
            VALUES (%s, 1, 'NEW', %s, %s, %s)
            """,
            (record_id, list(item), digest, Jsonb(item)),
        )
        _save_details(cur, record_id, item)
        return "NEW"

    cur.execute(
        """SELECT id, payload, content_hash, current_version
           FROM records WHERE source_id=%s AND external_id=%s FOR UPDATE""",
        (source_id, ext_id),
    )
    record_id, prior_payload, old_digest, old_version = cur.fetchone()
    if old_digest == digest:
        cur.execute("UPDATE records SET last_seen_at=now() WHERE id=%s", (record_id,))
        return "UNCHANGED"

    version = old_version + 1
    cur.execute(
        """UPDATE records SET title=%s, url=%s, payload=%s, content_hash=%s,
            current_version=%s, last_seen_at=now() WHERE id=%s""",
        (title, url, Jsonb(item), digest, version, record_id),
    )
    cur.execute(
        """
        INSERT INTO record_versions
            (record_id, version, change_type, changed_fields, content_hash, payload)
        VALUES (%s, %s, 'UPDATED', %s, %s, %s)
        """,
        (record_id, version, changed_fields(prior_payload, item), digest, Jsonb(item)),
    )
    _save_details(cur, record_id, item)
    return "UPDATED"
