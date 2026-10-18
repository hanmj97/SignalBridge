"""V2-10: process member-requested GitHub refreshes (queue poll every minute).

The existing signalbridge_collector_30m DAG remains the source of scheduled
periodic refreshes. This DAG only processes explicit per-member requests.
It starts paused until migration 019 has been applied.
"""
from __future__ import annotations

from datetime import timedelta
import logging

import pendulum
from airflow import DAG
from airflow.operators.python import PythonOperator

LOG = logging.getLogger(__name__)


def process_refresh_requests() -> None:
    from collector.refresh_worker import run_pending

    stats = run_pending()
    LOG.info("GitHub manual refresh: claimed=%d succeeded=%d failed=%d",
             stats["claimed"], stats["succeeded"], stats["failed"])
    if stats["failed"]:
        raise RuntimeError("A GitHub refresh failed. Check source ingestion_runs and retry.")


with DAG(
    dag_id="signalbridge_github_manual_refresh",
    description="Consume authenticated member GitHub repository refresh requests",
    schedule="* * * * *",
    start_date=pendulum.datetime(2026, 10, 1, tz="Asia/Seoul"),
    catchup=False,
    max_active_runs=1,
    default_args={"owner": "signalbridge", "retries": 0,
                  "execution_timeout": timedelta(minutes=8)},
    tags=["signalbridge", "engineering", "github", "manual-refresh"],
    is_paused_upon_creation=True,
) as dag:
    PythonOperator(task_id="consume_refresh_requests",
                   python_callable=process_refresh_requests)
