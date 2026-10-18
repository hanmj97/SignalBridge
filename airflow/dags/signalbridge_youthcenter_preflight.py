"""V2-4: manual-only OFFLINE XML validation DAG.

No network access, no DB writes, no API key, no Discord. This is intentionally
not the final production Youth Center ingestion DAG.
"""
from datetime import timedelta
import pendulum
from airflow import DAG
from airflow.operators.python import PythonOperator

FIXTURE = '/opt/airflow/collector/fixtures/youthcenter_synthetic_v24.xml'
UPDATED = '/opt/airflow/collector/fixtures/youthcenter_synthetic_v24_updated.xml'


def check_baseline():
    from pathlib import Path
    from collector.youthcenter_v24 import parse_synthetic_feed
    rows = parse_synthetic_feed(Path(FIXTURE).read_bytes())
    if len(rows) != 2 or not all(r['is_demo'] for r in rows):
        raise ValueError('synthetic feed did not pass quality validation')
    print('OFFLINE preflight: 2 synthetic items validated; no live API or DB access')


def check_change_detection_contract():
    from pathlib import Path
    from collector.youthcenter_v24 import parse_synthetic_feed
    from collector.logic import changed_fields, content_hash
    previous = parse_synthetic_feed(Path(FIXTURE).read_bytes())
    current = parse_synthetic_feed(Path(UPDATED).read_bytes())
    if len(previous)!=len(current) or previous[1]!=current[1]:
        raise ValueError('unexpected change in synthetic feed')
    if content_hash(previous[0])==content_hash(current[0]):
        raise ValueError('synthetic update was not detected')
    if changed_fields(previous[0], current[0])!=['application_deadline']:
        raise ValueError('unexpected changed fields')
    print('OFFLINE preflight: single date change recognized; no notifications sent')


with DAG(
    dag_id='signalbridge_youthcenter_offline_preflight',
    description='V2-4 offline validation only; safe while API key is pending',
    schedule=None,
    start_date=pendulum.datetime(2026,10,1,tz='Asia/Seoul'),
    catchup=False,
    is_paused_upon_creation=True,
    max_active_runs=1,
    default_args={'owner':'signalbridge','retries':0,'execution_timeout':timedelta(minutes=2)},
    tags=['signalbridge','youthcenter','offline','demo'],
) as dag:
    baseline = PythonOperator(task_id='validate_synthetic_xml',python_callable=check_baseline)
    change = PythonOperator(task_id='verify_change_contract',python_callable=check_change_detection_contract)
    baseline >> change
