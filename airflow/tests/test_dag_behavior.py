"""Behavior tests for the DAG, running without the Airflow package installed."""
from __future__ import annotations

import importlib.util
import sys
import types
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

DAG_PATH = Path(__file__).resolve().parents[1] / 'dags' / 'signalbridge_collector.py'


class FakeDAG:
    def __init__(self, **kwargs):
        self.options = kwargs

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False


class FakePythonOperator:
    def __init__(self, **kwargs):
        self.options = kwargs
        self.next_task = None

    def __rshift__(self, other):
        self.next_task = other
        return other


def load_dag_for_test():
    airflow = types.ModuleType('airflow')
    airflow.DAG = FakeDAG
    airflow_operators = types.ModuleType('airflow.operators')
    airflow_python = types.ModuleType('airflow.operators.python')
    airflow_python.PythonOperator = FakePythonOperator
    pendulum = types.ModuleType('pendulum')
    pendulum.datetime = lambda *args, **kwargs: datetime(*args, tzinfo=timezone.utc)

    modules = {
        'airflow': airflow,
        'airflow.operators': airflow_operators,
        'airflow.operators.python': airflow_python,
        'pendulum': pendulum,
    }
    with patch.dict(sys.modules, modules):
        spec = importlib.util.spec_from_file_location('_signalbridge_dag_test', DAG_PATH)
        module = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(module)
    return module


class DagBehaviorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dag_module = load_dag_for_test()

    def test_two_tasks_in_correct_order(self):
        m = self.dag_module
        self.assertEqual(m.collect.options['task_id'], 'collect_github_releases')
        self.assertEqual(m.collect.next_task, m.verify)
        self.assertEqual(m.verify.options['task_id'], 'verify_source_freshness')
        self.assertEqual(m.dag.options['max_active_runs'], 1)

    def test_collector_nonzero_exit_raises(self):
        fake_collector = types.ModuleType('collector')
        fake_main = types.ModuleType('collector.main')
        fake_main.main = lambda: 1
        with patch.dict(sys.modules, {'collector': fake_collector, 'collector.main': fake_main}):
            with self.assertRaises(RuntimeError):
                self.dag_module.run_existing_collector()

    def test_collector_success_does_not_raise(self):
        fake_collector = types.ModuleType('collector')
        fake_main = types.ModuleType('collector.main')
        fake_main.main = lambda: 0
        with patch.dict(sys.modules, {'collector': fake_collector, 'collector.main': fake_main}):
            self.dag_module.run_existing_collector()

    def test_final_failure_callback_skips_without_webhook(self):
        m = self.dag_module
        with patch.dict('os.environ', {'SIGNALBRIDGE_DISCORD_WEBHOOK_URL': ''}):
            with patch.object(m.requests, 'post') as post:
                m.notify_discord_on_failure({'task_instance': None, 'logical_date': None})
                post.assert_not_called()


if __name__ == '__main__':
    unittest.main()
