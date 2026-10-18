"""V2-5B Yongin official API -> isolated SignalBridge lab database.

Never fabricate an article permalink. `records.url` is the *verified data.go.kr
DATASET catalogue page*, and opportunity_details.link_kind='DATASET'.
No automated scheduling or Discord delivery is enabled by this tool.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path
from collector.providers.yongin_jobs import fetch_live, preview, ProviderError, JobPreview
from collector.opportunity_logic import normalize_opportunity

SOURCE_KEY = 'yongin_jobs_official'
SOURCE_PROVIDER = 'yongin_jobs'
SOURCE_LABEL = '경기도 용인시 채용정보 (공공데이터)'
SOURCE_RESOURCE = '4050000/eplymtexam/getEplymtexam'
DATASET_URL = 'https://www.data.go.kr/data/15097328/openapi.do'
LAB_DB = 'signalbridge_v2_lab'


def to_canonical(row: JobPreview) -> dict:
    """Only map confirmed provider fields; do not infer eligibility or job location."""
    if not row.source_id or not row.title:
        raise ValueError('missing required ID or title')
    if row.source_type != '채용정보':
        raise ValueError('only recruitment notices may be imported as JOB')
    data = {
        'external_id': 'yongin:' + row.source_id,
        'title': row.title,
        'url': DATASET_URL,
        'kind': 'JOB',
        'organization': '경기도 용인시',
        'category': row.source_type,
        'region_codes': [], # provider location is NOT necessarily job location
        'application_start': row.starts_on,
        'application_deadline': row.ends_on,
        'status': 'UNKNOWN', # rcpt_yn is not enough to infer today's status
        'age_min': None, 'age_max': None, 'employment_type': None,
        'eligibility_summary': None,
        'benefit_summary': None,
        'source_published_at': None,
    }
    return normalize_opportunity(data, is_demo=False)


def normalize_batch(rows: list[JobPreview]) -> list[dict]:
    # Reject unrelated exam announcements and pass/fail notices even if the
    # upstream provider ignores our server-side `type=채용정보` query parameter.
    items = [to_canonical(row) for row in rows if row.source_type == '채용정보']
    keys = [r['external_id'] for r in items]
    if len(keys) != len(set(keys)):
        raise ValueError('duplicate source IDs in returned API results')
    return items


def _write_in_lab(items: list[dict]) -> tuple[int,int,int]:
    """Atomic write; protect every live row with source identity + DB name checks."""
    from collector.main import connect
    from collector.opportunity_store import save_opportunity
    old = os.environ.get('POSTGRES_DB')
    os.environ['POSTGRES_DB'] = LAB_DB
    try:
        with connect() as conn:
            with conn.cursor() as cur:
                cur.execute('SELECT current_database()')
                if cur.fetchone()[0] != LAB_DB:
                    raise RuntimeError('DB safety check failed; refusing write')
            # Each run is committed separately, so errors can be recorded safely.
            with conn.transaction():
                with conn.cursor() as cur:
                    cur.execute("""INSERT INTO sources (source_key,label,provider,resource)
                        VALUES (%s,%s,%s,%s) ON CONFLICT (source_key) DO NOTHING""",
                        (SOURCE_KEY,SOURCE_LABEL,SOURCE_PROVIDER,SOURCE_RESOURCE))
                    cur.execute("""SELECT id,provider,resource FROM sources
                        WHERE source_key=%s FOR UPDATE""",(SOURCE_KEY,))
                    row=cur.fetchone()
                    if not row or (row[1],row[2]) != (SOURCE_PROVIDER,SOURCE_RESOURCE):
                        raise RuntimeError('provider identity conflict')
                    source_id=row[0]
                    # Update only verified source display label; never touch member DB.
                    cur.execute('UPDATE sources SET label=%s WHERE id=%s', (SOURCE_LABEL, source_id))
                    cur.execute('INSERT INTO ingestion_runs (source_id) VALUES (%s) RETURNING id', (source_id,))
                    run_id=cur.fetchone()[0]
            try:
                with conn.transaction():
                    counters={'NEW':0,'UPDATED':0,'UNCHANGED':0}
                    with conn.cursor() as cur:
                        # Guarantee migration applied; fail before inserting records.
                        cur.execute("""SELECT 1 FROM information_schema.columns
                            WHERE table_name='opportunity_details' AND column_name='link_kind' """)
                        if cur.fetchone() is None:
                            raise RuntimeError('run migration 017 on lab DB before import')
                        for item in items:
                            status=save_opportunity(cur,source_id,item)
                            counters[status]+=1
                            cur.execute("""UPDATE opportunity_details SET link_kind='DATASET'
                                WHERE record_id=(SELECT id FROM records
                                  WHERE source_id=%s AND external_id=%s)""",
                                (source_id,item['external_id']))
                        cur.execute("""UPDATE ingestion_runs SET status='SUCCESS', finished_at=now(),
                            fetched_count=%s,new_count=%s,updated_count=%s WHERE id=%s""",
                            (len(items),counters['NEW'],counters['UPDATED'],run_id))
                        cur.execute('UPDATE sources SET last_checked_at=now() WHERE id=%s',(source_id,))
                return counters['NEW'], counters['UPDATED'], counters['UNCHANGED']
            except Exception:
                with conn.transaction():
                    with conn.cursor() as cur:
                        cur.execute("""UPDATE ingestion_runs SET status='FAILED', finished_at=now(),
                            error_message='Yongin batch validation/DB write failed' WHERE id=%s""",(run_id,))
                raise
    finally:
        if old is None: os.environ.pop('POSTGRES_DB', None)
        else: os.environ['POSTGRES_DB']=old


def main(argv=None):
    p=argparse.ArgumentParser(description='Yongin official data, opt-in LAB importer')
    p.add_argument('--live',action='store_true',help='Request real API')
    p.add_argument('--permit-network',action='store_true',help='Explicit network permission')
    p.add_argument('--write-lab',action='store_true',help='Explicit write to isolated lab DB')
    p.add_argument('--page',type=int,default=1,help='API page number (1-10)')
    p.add_argument('--rows',type=int,default=20,help='API rows per page (1-20)')
    p.add_argument('--start',help='YYYY-MM-DD')
    p.add_argument('--end',help='YYYY-MM-DD')
    args=p.parse_args(argv)
    try:
        if not args.live or not args.permit_network:
            raise ProviderError('--live --permit-network required for a live request')
        from dotenv import load_dotenv
        load_dotenv(Path(__file__).resolve().parents[1]/'.env')
        key=os.getenv('YONGIN_API_SERVICE_KEY','').strip()
        if not key: raise ProviderError('YONGIN_API_SERVICE_KEY is missing')
        rows=preview(fetch_live(key,page=args.page,rows=args.rows,start=args.start,end=args.end))
        items=normalize_batch(rows)
        print(f'LIVE Yongin preview: {len(items)} records; individual article URL NOT supplied')
        for item in items[:5]:
            print(' - '+item['external_id']+': '+item['title'][:100])
        if not args.write_lab:
            print('READ ONLY: add --write-lab for explicit lab DB import. No DB writes performed.')
            return 0
        if not items:
            print('No records returned; no data written. Try another date range.')
            return 0
        result=_write_in_lab(items)
        print(f'LAB DB ({LAB_DB}) import OK: new={result[0]}, updated={result[1]}, unchanged={result[2]}')
        print('Data is public API information, not verified original posting URLs.')
        return 0
    except (ProviderError, ValueError) as e:
        # Safe exceptions from our parser, without secrets or request URLs.
        print('Yongin import failed: invalid source response or permissions; details hidden',file=sys.stderr)
        return 1
    except Exception as e:
        print(f'Yongin import failed: {type(e).__name__}; check migration and DB configuration',file=sys.stderr)
        return 1

if __name__=='__main__':
    raise SystemExit(main())
