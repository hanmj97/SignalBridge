"""Dedicated V2-3 alert DAG. Does not alter the existing GitHub release DAG."""
from datetime import timedelta
import pendulum
from airflow import DAG
from airflow.operators.python import PythonOperator


def queue_opportunity_events():
    from opportunity_alert_notifier import queue_events
    queue_events()


def deliver_opportunity_events():
    from opportunity_alert_notifier import deliver_events
    deliver_events()


with DAG(
    dag_id='signalbridge_opportunity_alerts_30m',
    description='Match verified opportunity changes to private member subscriptions and Discord',
    schedule='*/30 * * * *',
    start_date=pendulum.datetime(2026, 10, 1, tz='Asia/Seoul'),
    catchup=False,
    max_active_runs=1,
    default_args={'owner':'signalbridge','retries':1,'retry_delay':timedelta(minutes=3)},
    tags=['signalbridge','opportunities','member-alerts'],
    is_paused_upon_creation=True,  # Manually enable after validation
) as dag:
    match = PythonOperator(task_id='match_opportunity_versions',python_callable=queue_opportunity_events)
    notify = PythonOperator(task_id='notify_opportunity_subscribers',python_callable=deliver_opportunity_events)
    match >> notify
