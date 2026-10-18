"""Static contract checks that run without installing Airflow itself."""
from __future__ import annotations

import ast
import pathlib
import unittest

DAG_FILE = pathlib.Path(__file__).resolve().parents[1] / 'dags' / 'signalbridge_collector.py'


class DagContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = DAG_FILE.read_text(encoding='utf-8')
        cls.tree = ast.parse(cls.code)

    def test_python_is_valid(self):
        compile(self.tree, str(DAG_FILE), 'exec')

    def test_expected_tasks_defined(self):
        self.assertIn('collect_github_releases', self.code)
        self.assertIn('verify_source_freshness', self.code)

    def test_30_minute_schedule_is_present(self):
        self.assertIn('*/30 * * * *', self.code)
        self.assertIn('catchup=False', self.code)
        self.assertIn('max_active_runs=1', self.code)

    def test_failure_handling_is_present(self):
        self.assertIn('on_failure_callback', self.code)
        self.assertIn('"retries": 2', self.code)
        self.assertIn('SIGNALBRIDGE_DISCORD_WEBHOOK_URL', self.code)

    def test_no_schema_reset_or_destructive_operation(self):
        for phrase in ('TRUNCATE TABLE', 'DROP TABLE', 'DELETE FROM records'):
            self.assertNotIn(phrase, self.code)


if __name__ == '__main__':
    unittest.main()
