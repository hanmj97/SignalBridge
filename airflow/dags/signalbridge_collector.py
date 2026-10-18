"""SignalBridge: GitHub releases -> PostgreSQL, every 30 minutes (KST).

This DAG imports the EXISTING collector rather than duplicating its logic.
Airflow's SQLite metadata database is independent of the SignalBridge
PostgreSQL database.
"""
from __future__ import annotations

import logging
import os
from datetime import datetime, timedelta, timezone

import pendulum
import requests
from airflow import DAG
from airflow.operators.python import PythonOperator

LOG = logging.getLogger(__name__)


def run_existing_collector() -> None:
    """Turn the collector's exit code into an Airflow task result."""
    # Lazy import: DAG parsing must not open external DB/API connections.
    from collector.main import main

    code = main()
    if code != 0:
        raise RuntimeError(f"SignalBridge collector failed (exit code {code}); inspect ingestion_runs")


def verify_source_freshness() -> None:
    """Every enabled GitHub source must have a recent successful check."""
    from collector.main import connect

    with connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT label, last_checked_at
                FROM sources
                WHERE enabled = TRUE AND provider = 'github_releases'
                ORDER BY id
                """
            )
            sources = cur.fetchall()

    if not sources:
        raise RuntimeError("No enabled GitHub sources to verify")

    now = datetime.now(timezone.utc)
    stale = [
        label
        for label, checked in sources
        if checked is None or now - checked > timedelta(minutes=15)
    ]
    if stale:
        raise RuntimeError("No recent successful collection for: " + ", ".join(stale))
    LOG.info("Freshness check passed for %d enabled GitHub sources", len(sources))


def notify_discord_on_failure(context: dict) -> None:
    """Alert after final task failure. Never log or expose the webhook URL."""
    webhook = os.getenv("SIGNALBRIDGE_DISCORD_WEBHOOK_URL", "").strip()
    if not webhook:
        LOG.warning("Discord webhook is not configured; skipping failure alert")
        return

    task_instance = context.get("task_instance")
    task_id = getattr(task_instance, "task_id", "unknown")
    dag_id = getattr(task_instance, "dag_id", "signalbridge_collector_30m")
    logical_date = context.get("logical_date")
    message = (
        "🚨 SignalBridge 데이터 수집 파이프라인 실패\n"
        f"DAG: `{dag_id}`\nTask: `{task_id}`\n"
        f"Logical date: `{logical_date}`\n"
        "Airflow(로컬 8082 포트)에서 로그를 확인해 주세요."
    )
    try:
        # No retries here: Airflow is already reporting the final task failure.
        response = requests.post(webhook, json={"content": message}, timeout=10)
        if response.status_code >= 400:
            LOG.warning("Discord notification was rejected (HTTP %s)", response.status_code)
    except requests.RequestException:
        # An exception may include the secret URL, so don't log its text.
        LOG.warning("Discord notification could not be delivered")


with DAG(
    dag_id="signalbridge_collector_30m",
    description="Fetch public GitHub releases and validate freshness every 30 minutes",
    schedule="*/30 * * * *",
    start_date=pendulum.datetime(2026, 10, 1, tz="Asia/Seoul"),
    catchup=False,
    max_active_runs=1,
    dagrun_timeout=timedelta(minutes=25),
    default_args={
        "owner": "signalbridge",
        "retries": 2,
        "retry_delay": timedelta(minutes=3),
        "execution_timeout": timedelta(minutes=8),
        "on_failure_callback": notify_discord_on_failure,
    },
    tags=["signalbridge", "etl", "github", "data-quality"],
    is_paused_upon_creation=False,
) as dag:
    collect = PythonOperator(
        task_id="collect_github_releases",
        python_callable=run_existing_collector,
    )
    verify = PythonOperator(
        task_id="verify_source_freshness",
        python_callable=verify_source_freshness,
    )
    # New in Phase 8: match newly detected record_versions and deliver eligible alerts.
    # Imports occur at task execution time so DAG parsing stays side-effect-free.
    def queue_watches():
        from watch_notifier import match_watch_events
        match_watch_events()

    def deliver_watches():
        from watch_notifier import send_watch_notifications
        send_watch_notifications()

    queue = PythonOperator(task_id="queue_watch_events", python_callable=queue_watches)
    dispatch = PythonOperator(task_id="notify_watch_events", python_callable=deliver_watches)
    collect >> verify >> queue >> dispatch
