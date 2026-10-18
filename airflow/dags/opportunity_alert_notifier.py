"""V2-3 independent opportunity matcher and member-only Discord dispatcher.

Two PostgreSQL databases:
 - signalbridge: app_users, rules, events, encrypted member Discord URLs
 - signalbridge_v2_lab (local): opportunity records and versions
Never forwards synthetic/demo rows. Does not modify legacy watch_events.
"""
from __future__ import annotations

import logging
import os
from datetime import timedelta
from urllib.parse import urlparse

import requests

LOG = logging.getLogger(__name__)
VALID_KINDS = {'POLICY', 'BENEFIT', 'JOB', 'PROGRAM'}

RULES_SQL = """
SELECT r.id,r.keyword,r.kind,r.region,r.age,r.created_at
FROM opportunity_alert_rules r
WHERE r.enabled=TRUE
ORDER BY r.id
"""

# The catalog DB has no member data; PostgreSQL cannot JOIN across these databases.
# Never use newest current-version state to fabricate historical matching facts;
# the event title is taken from the captured version payload.
CHANGES_SQL = """
SELECT v.id AS version_id, r.id AS record_id,
  coalesce(v.payload->>'title',r.title) AS title,
  s.label AS source_label, v.change_type, v.detected_at,
  d.kind, d.region_codes, d.age_min, d.age_max, d.organization, d.category
FROM record_versions v
JOIN records r ON r.id=v.record_id
JOIN sources s ON s.id=r.source_id
JOIN opportunity_details d ON d.record_id=r.id
WHERE d.is_demo=FALSE AND d.link_kind='ARTICLE' AND v.detected_at > %s
ORDER BY v.id ASC
LIMIT 5001
"""

INSERT_SQL = """
INSERT INTO opportunity_alert_events
  (rule_id,catalog_version_id,catalog_record_id,title,source_label,change_type,detected_at)
VALUES (%s,%s,%s,%s,%s,%s,%s)
ON CONFLICT (rule_id,catalog_version_id) DO NOTHING
"""

PENDING_SQL = """
SELECT e.id, e.title, e.source_label, e.change_type, e.catalog_record_id,
       c.webhook_secret
FROM opportunity_alert_events e
JOIN opportunity_alert_rules r ON r.id=e.rule_id AND r.enabled=TRUE
JOIN member_discord_channels c ON c.user_id=r.user_id AND c.enabled=TRUE
WHERE e.status='PENDING' AND e.attempts<3
  AND e.created_at>c.activated_at AND e.detected_at>c.activated_at
ORDER BY e.id LIMIT 10
"""


def matches(rule: tuple, change: tuple) -> bool:
    """Match only confirmed provider data; missing age bounds are not eligibility proof."""
    _, keyword, kind, region, age, _created = rule
    (_version_id, _record_id, title, label, _change_type, _detected,
     item_kind, regions, minimum, maximum, organization, category) = change
    if kind is not None and item_kind != kind:
        return False
    if region and region not in (regions or []) and '전국' not in (regions or []):
        return False
    if age is not None:
        if minimum is None or maximum is None or not(minimum <= age <= maximum):
            return False
    if keyword:
        haystack = ' '.join(str(x or '') for x in (title,label,organization,category)).casefold()
        if keyword.casefold() not in haystack:
            return False
    return True


def queue_events() -> int:
    import psycopg
    from collector.main import connect
    with connect() as main:
        with main.cursor() as cur:
            cur.execute(RULES_SQL)
            rules = cur.fetchall()
    if not rules:
        LOG.info('No enabled V2 subscriptions; skipping match')
        return 0
    earliest = min(r[5] for r in rules)
    # Seven-day catch-up window bounds an accidental explosion on long outages.
    from datetime import datetime, timezone
    since = max(earliest, datetime.now(timezone.utc) - timedelta(days=7))
    dbname = os.getenv('SIGNALBRIDGE_V2_DB_NAME','signalbridge_v2_lab')
    if not dbname.startswith('signalbridge_v2'):
        raise ValueError('V2 test database name not in allowlist')
    settings = {
        'host': os.environ.get('SIGNALBRIDGE_DB_HOST','postgres'),
        'port': os.environ.get('SIGNALBRIDGE_DB_PORT','5432'),
        'dbname': dbname,
        'user': os.environ.get('POSTGRES_USER','signalbridge'),
        'password': os.environ['POSTGRES_PASSWORD'],
        'connect_timeout': 5,
    }
    with psycopg.connect(**settings) as catalog:
        with catalog.cursor() as cur:
            cur.execute(CHANGES_SQL,(since,))
            changes=cur.fetchall()
    if len(changes)>5000:
        raise RuntimeError('V2 catalog change window exceeded 5000; tighten/run batch backfill before continuing')
    queued=0
    with connect() as main:
        with main.transaction():
            with main.cursor() as cur:
                for change in changes:
                    version_id, record_id, title, label, ch_type, detected = change[:6]
                    for rule in rules:
                        if detected <= rule[5] or not matches(rule,change):
                            continue
                        cur.execute(INSERT_SQL,(rule[0],version_id,record_id,title,label,ch_type,detected))
                        queued += cur.rowcount
    LOG.info('V2 opportunity alert candidates queued: %d (synthetic rows excluded)',queued)
    return queued


def _clean(value, limit=250):
    return str(value or '').replace('@','@\u200b').replace('`',"'").replace('\r',' ').replace('\n',' ')[:limit]


def format_message(event):
    event_id,title,source,change_type,record_id,*_ = event
    action = '새 정책·채용공고' if change_type=='NEW' else '공고 내용 변경'
    return ('🔔 SignalBridge 정책·채용 관심 알림\n'
      f'{action}: **{_clean(title,280)}**\n'
      f'정보 출처: {_clean(source,100)}\n'
      f'알림 번호: {event_id} / 항목: {record_id}\n'
      '자세한 내용과 실제 지원 가능 여부는 SignalBridge에서 확인해 주세요.')[:1750]


def deliver_events() -> int:
    import binascii
    from collector.main import connect
    from watch_notifier import _decode_member_hook
    if not os.getenv('SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY','').strip():
        LOG.info('Discord encryption key absent; V2 events remain pending')
        return 0
    sent=0
    with connect() as db:
        with db.cursor() as cur:
            cur.execute('SELECT pg_try_advisory_lock(%s)',(711012,))
            acquired=cur.fetchone()[0]
        db.commit()
        if not acquired:
            LOG.info('V2 Discord delivery already running')
            return 0
        try:
            with db.cursor() as cur:
                cur.execute(PENDING_SQL)
                events=cur.fetchall()
            db.commit()
            for event in events:
                try:
                    url=_decode_member_hook(event[5],os.environ['SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY'])
                except (ValueError, binascii.Error, UnicodeDecodeError, TypeError):
                    LOG.error('V2 member Discord encrypted destination could not be decrypted')
                    break
                status='NETWORK'; success=False; rate_limited=False
                try:
                    response=requests.post(url,json={'content':format_message(event),'allowed_mentions':{'parse':[]}},
                                           timeout=10,allow_redirects=False)
                    status=str(response.status_code)
                    success=200<=response.status_code<300
                    rate_limited=response.status_code==429
                except requests.RequestException:
                    pass  # never expose secret webhook URL
                with db.transaction():
                    with db.cursor() as cur:
                        cur.execute("""
                          UPDATE opportunity_alert_events SET
                            attempts=CASE WHEN %s THEN attempts ELSE attempts+1 END,
                            last_attempt_at=now(),
                            status=CASE WHEN %s THEN 'SENT'
                              WHEN NOT %s AND (attempts+1>=3 OR %s IN ('401','403','404')) THEN 'FAILED'
                              ELSE 'PENDING' END,
                            delivered_at=CASE WHEN %s THEN now() ELSE delivered_at END,
                            error_code=CASE WHEN %s THEN NULL ELSE %s END
                          WHERE id=%s AND status='PENDING'
                        """,(rate_limited,success,rate_limited,status,success,success,status,event[0]))
                if success:
                    sent+=1
                else:
                    LOG.warning('V2 Discord event delivery failed: event %s, code %s',event[0],status)
                    break
        finally:
            with db.cursor() as cur:cur.execute('SELECT pg_advisory_unlock(%s)',(711012,))
            db.commit()
    LOG.info('V2 Discord alerts sent: %d',sent)
    return sent
