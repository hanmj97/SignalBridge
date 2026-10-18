"""V2-5C: guarded daily Yongin open API ingestion into SignalBridge V2 LAB.

New DAG is paused on registration. Set SIGNALBRIDGE_V2_LIVE_INGEST_ENABLED=true
and explicitly unpause/trigger after manual preflight. No member alerts here.
"""
from __future__ import annotations

import logging
import os
from datetime import timedelta

import pendulum
from airflow import DAG
from airflow.exceptions import AirflowSkipException
from airflow.operators.python import PythonOperator

LOG = logging.getLogger(__name__)


def guard_permissions():
    if os.environ.get('SIGNALBRIDGE_V2_LIVE_INGEST_ENABLED', '').strip().lower() != 'true':
        raise AirflowSkipException('Live ingest is disabled; enable explicitly after verification')
    if not os.environ.get('YONGIN_API_SERVICE_KEY', '').strip():
        raise RuntimeError('Yongin API key missing (do not expose key in logs)')
    if os.environ.get('POSTGRES_DB', 'signalbridge') == 'signalbridge_v2_lab':
        raise RuntimeError('Main member DB must remain the default DB')
    # The importer itself forces LAB_DB and verifies current_database() before writes.
    LOG.info('Yongin live ingest preflight passed (LAB target, dataset links, no Discord)')


def fetch_validate_store():
    from collector.yongin_scheduler_v25c import collect_once
    result = collect_once(
        os.environ['YONGIN_API_SERVICE_KEY'],
        pages=3,
        rows=20,
        persist=True,
    )
    LOG.info('Yongin result pages=%d accepted=%d new=%d updated=%d unchanged=%d',
             result.pages_requested, result.rows_collected,
             result.inserted, result.updated, result.unchanged)


with DAG(
    dag_id='signalbridge_yongin_live_daily',
    description='Guarded Yongin public jobs -> V2 LAB, no Discord',
    schedule='0 9 * * *',
    start_date=pendulum.datetime(2026, 10, 18, tz='Asia/Seoul'),
    catchup=False,
    is_paused_upon_creation=True,
    max_active_runs=1,
    dagrun_timeout=timedelta(minutes=15),
    default_args={
        'owner': 'signalbridge',
        'retries': 1,
        'retry_delay': timedelta(minutes=3),
        'execution_timeout': timedelta(minutes=8),
    },
    tags=['signalbridge', 'v2', 'recruitment', 'lab', 'manual-enable'],
) as dag:
    preflight = PythonOperator(task_id='check_live_ingest_enabled', python_callable=guard_permissions)
    collect = PythonOperator(task_id='collect_validate_store_yongin', python_callable=fetch_validate_store)
    preflight >> collect
