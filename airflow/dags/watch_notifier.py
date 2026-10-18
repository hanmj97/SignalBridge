"""Phase 8 watch notifications. All SQL uses parameters; webhook stays in Airflow env.

Matched version events are created only for versions detected AFTER the rule was
registered. Unique(rule_id, record_version_id) makes the matching step idempotent.
Do not import collector / access DB at DAG parsing time.
"""
from __future__ import annotations

import logging
import os
import re
import base64
import binascii
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag
from urllib.parse import urlparse

import requests

LOG = logging.getLogger(__name__)

MATCH_EVENTS_SQL = """
INSERT INTO watch_events (rule_id, record_version_id)
SELECT w.id, v.id
FROM watch_rules w
JOIN record_versions v ON v.detected_at > w.created_at
JOIN records r ON r.id = v.record_id
JOIN sources s ON s.id = r.source_id
WHERE w.enabled = TRUE
  AND (w.source_id IS NULL OR w.source_id = r.source_id)
  AND (
    strpos(lower(coalesce(v.payload->>'title', r.title)), lower(w.keyword)) > 0
    OR strpos(lower(s.label), lower(w.keyword)) > 0
  )
ON CONFLICT (rule_id, record_version_id) DO NOTHING
"""

# Phase 9 privacy boundary: the old shared Discord webhook must never receive
# events belonging to signed-in members. Member events remain PENDING until
# Phase 10 provides per-member opt-in delivery destinations.
PENDING_SQL = """
SELECT e.id, w.keyword, s.label, coalesce(v.payload->>'title', r.title) AS title,
       r.url, v.change_type, v.version
FROM watch_events e
JOIN watch_rules w ON w.id = e.rule_id
JOIN record_versions v ON v.id = e.record_version_id
JOIN records r ON r.id = v.record_id
JOIN sources s ON s.id = r.source_id
WHERE e.status = 'PENDING' AND w.enabled = TRUE AND w.user_id IS NULL AND e.attempts < 3
ORDER BY e.id LIMIT 10
"""


def match_watch_events() -> int:
    from collector.main import connect
    with connect() as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.execute(MATCH_EVENTS_SQL)
                count = cur.rowcount
    LOG.info('Watch notification candidates queued: %d', count)
    return count


def _safe_url(url: str) -> str:
    """Only link known GitHub release pages in Discord."""
    try:
        u = urlparse(url)
        if u.scheme == 'https' and u.hostname == 'github.com' and u.username is None:
            return url
    except ValueError:
        pass
    return ''


def _plain(value: str, limit: int) -> str:
    # Prevent Markdown mentions and code fence/newline injection in Discord.
    return str(value or '').replace('@', '@\u200b').replace('`', "'").replace('\r', ' ').replace('\n', ' ')[:limit]


def message_for(row: tuple) -> str:
    event_id, keyword, source_label, title, url, change_type, version = row
    kind = '신규 릴리스' if change_type == 'NEW' else '릴리스 내용 변경'
    parts = [
        '🔔 SignalBridge 관심 키워드 알림',
        f'키워드: **{_plain(keyword, 80)}**',
        f'소스: {_plain(source_label, 100)}',
        f'{kind} (v{version}): {_plain(title, 350)}',
    ]
    link = _safe_url(url)
    if link:
        parts.append(link)
    parts.append(f'알림 이벤트 #{event_id}')
    return '\n'.join(parts)[:1750]


def send_watch_notifications() -> int:
    """Dispatch member events privately, then check disabled legacy/shared events."""
    member_sent = send_member_notifications()
    webhook = os.getenv('SIGNALBRIDGE_DISCORD_WEBHOOK_URL', '').strip()
    if not webhook:
        LOG.info('Watch events queued. No Discord webhook configured; delivery skipped.')
        return member_sent
    from collector.main import connect
    sent = 0
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(PENDING_SQL)
            pending = cur.fetchall()
        for row in pending:
            event_id = row[0]
            status_code = 'NETWORK'
            success = False
            rate_limited = False
            try:
                response = requests.post(
                    webhook,
                    json={'content': message_for(row), 'allowed_mentions': {'parse': []}},
                    timeout=10,
                )
                status_code = str(response.status_code)
                success = 200 <= response.status_code < 300
                rate_limited = response.status_code == 429
            except requests.RequestException:
                # Do not stringify exception: it could contain webhook credentials.
                pass
            with conn.transaction():
                with conn.cursor() as cur:
                    cur.execute("""
                        UPDATE watch_events
                        SET attempts = CASE WHEN %s THEN attempts ELSE attempts + 1 END,
                            last_attempt_at = now(),
                            status = CASE WHEN %s THEN 'SENT'
                                          WHEN attempts + 1 >= 3 AND NOT %s THEN 'FAILED'
                                          ELSE 'PENDING' END,
                            delivered_at = CASE WHEN %s THEN now() ELSE delivered_at END,
                            error_code = CASE WHEN %s THEN NULL ELSE %s END
                        WHERE id = %s AND status = 'PENDING'
                    """, (rate_limited, success, rate_limited, success, success, status_code, event_id))
            if success:
                sent += 1
            elif rate_limited:
                LOG.warning('Discord rate limit response; remaining watch notifications deferred')
                break
            else:
                LOG.warning('Watch event delivery unsuccessful (event %s, status %s)', event_id, status_code)
                # Avoid a large retry burst when webhook is invalid or offline.
                break
    LOG.info('Watch events sent this run: %d', sent)
    return sent + member_sent


# Phase 11: member-owned destinations. Never send a member event via the
# shared SIGNALBRIDGE_DISCORD_WEBHOOK_URL (reserved for DAG-failure alerts).
MEMBER_PENDING_SQL = """
SELECT e.id, w.keyword, s.label, coalesce(v.payload->>'title',r.title) AS title,
       r.url, v.change_type, v.version, c.webhook_secret
FROM watch_events e
JOIN watch_rules w ON w.id=e.rule_id AND w.user_id IS NOT NULL
JOIN member_discord_channels c ON c.user_id=w.user_id AND c.enabled=TRUE
JOIN record_versions v ON v.id=e.record_version_id
JOIN records r ON r.id=v.record_id
JOIN sources s ON s.id=r.source_id
WHERE e.status='PENDING' AND e.attempts<3 AND w.enabled=TRUE
  AND e.created_at>c.activated_at AND v.detected_at>c.activated_at
ORDER BY e.id LIMIT 10
"""

DISCORD_URL_RE = re.compile(r'https://discord\.com/api/webhooks/[0-9]{15,25}/[A-Za-z0-9_-]{30,180}\Z')


def _decode_member_hook(encrypted: str, key_text: str) -> str:
    """Decrypt Java's v1:base64(12-byte nonce||ciphertext||GCM tag)."""
    if not encrypted.startswith('v1:'):
        raise ValueError('ciphertext version')
    key = base64.b64decode(key_text, validate=True)
    if len(key) != 32:
        raise ValueError('AES key size')
    value = base64.b64decode(encrypted[3:], validate=True)
    if len(value) < 29:
        raise ValueError('ciphertext length')
    try:
        url = AESGCM(key).decrypt(value[:12], value[12:], None).decode('utf-8')
    except InvalidTag:
        raise ValueError('ciphertext integrity') from None
    if not DISCORD_URL_RE.fullmatch(url):
        raise ValueError('destination validation')
    return url


def send_member_notifications() -> int:
    """One worker at a time via PostgreSQL advisory lock; max ten per run.

    Delivery is at-least-once: if Discord accepted a message just before a
    crash, a subsequent run can send it again before SENT is committed.
    """
    key_text = os.getenv('SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY', '').strip()
    if not key_text:
        LOG.info('Member Discord key not configured; member alerts remain queued.')
        return 0
    from collector.main import connect
    sent = 0
    lock_id = 711011
    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute('SELECT pg_try_advisory_lock(%s)', (lock_id,))
            acquired = cur.fetchone()[0]
        conn.commit()
        if not acquired:
            LOG.info('Member alert worker is already running; skipping this invocation.')
            return 0
        try:
            with conn.cursor() as cur:
                cur.execute(MEMBER_PENDING_SQL)
                rows = cur.fetchall()
            conn.commit()
            for row in rows:
                event_id = row[0]
                try:
                    webhook = _decode_member_hook(row[7], key_text)
                except (ValueError, binascii.Error, UnicodeDecodeError, TypeError):
                    # Missing/rotated key must not consume or fail queued events.
                    # Restore the original key; never include the secret in logs.
                    LOG.error('Member Discord decryption failed. Check shared encryption key (event %s)', event_id)
                    break
                code = 'NETWORK'
                ok = False
                throttled = False
                try:
                    response = requests.post(
                        webhook,
                        json={'content': message_for(row[:7]),'allowed_mentions': {'parse': []}},
                        timeout=10, allow_redirects=False,
                    )
                    code = str(response.status_code)
                    ok = 200 <= response.status_code < 300
                    throttled = response.status_code == 429
                except requests.RequestException:
                    # Don't log exception object: it may carry the secret webhook URL.
                    pass
                with conn.transaction():
                    with conn.cursor() as cur:
                        cur.execute("""
                            UPDATE watch_events SET
                              attempts=CASE WHEN %s THEN attempts ELSE attempts+1 END,
                              last_attempt_at=now(),
                              status=CASE WHEN %s THEN 'SENT'
                                WHEN NOT %s AND (attempts+1>=3 OR %s IN ('401','403','404')) THEN 'FAILED'
                                ELSE 'PENDING' END,
                              delivered_at=CASE WHEN %s THEN now() ELSE delivered_at END,
                              error_code=CASE WHEN %s THEN NULL ELSE %s END
                            WHERE id=%s AND status='PENDING'
                        """, (throttled, ok, throttled, code, ok, ok, code, event_id))
                if ok:
                    sent += 1
                else:
                    LOG.warning('Discord member alert not delivered (event %s, code %s)', event_id, code)
                    # A failing endpoint should not cause a burst of repeated sends.
                    break
        finally:
            with conn.cursor() as cur:
                cur.execute('SELECT pg_advisory_unlock(%s)', (lock_id,))
            conn.commit()
    LOG.info('Member Discord alerts sent: %d', sent)
    return sent
