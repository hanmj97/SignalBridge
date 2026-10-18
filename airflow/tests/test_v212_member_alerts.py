"""Regression checks for Engineering V2-12 member-only release notifications.

Pure/offline checks: no real Discord webhook, DB, or network traffic.
"""
from pathlib import Path
import ast
import importlib.util
import unittest
from unittest.mock import patch

DAGS = Path(__file__).resolve().parents[1] / 'dags'
MODULE_PATH = DAGS / 'watch_notifier.py'
SPEC = importlib.util.spec_from_file_location('watch_notifier_v212', MODULE_PATH)
notifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(notifier)


class MemberWatchPrivacyTests(unittest.TestCase):
    def test_notifier_modules_parse(self):
        for name in ('watch_notifier.py', 'signalbridge_github_manual_refresh.py'):
            with self.subTest(name=name):
                ast.parse((DAGS / name).read_text(encoding='utf-8'))

    def test_member_matching_requires_active_subscription_and_new_version(self):
        sql = notifier.MATCH_EVENTS_SQL
        self.assertIn('sub.user_id = w.user_id', sql)
        self.assertIn('sub.source_id = r.source_id', sql)
        self.assertIn('v.detected_at > sub.created_at', sql)
        self.assertIn('v.detected_at > w.created_at', sql)

    def test_member_delivery_checks_current_subscription_again(self):
        sql = notifier.MEMBER_PENDING_SQL
        self.assertIn('sub.user_id=w.user_id', sql)
        self.assertIn('sub.source_id=r.source_id', sql)
        self.assertIn('c.user_id=w.user_id', sql)
        self.assertIn('v.detected_at>c.activated_at', sql)

    def test_all_releases_matches_only_source_specific_watch(self):
        sql = notifier.MATCH_EVENTS_SQL
        self.assertIn("w.keyword = '모든 릴리스' AND w.source_id IS NOT NULL", sql)

    def test_existing_duplicate_event_guard_is_preserved(self):
        self.assertIn('ON CONFLICT (rule_id, record_version_id) DO NOTHING',
                      notifier.MATCH_EVENTS_SQL)

    def test_manual_dag_runs_alerts_after_successful_refresh(self):
        dag_source = (DAGS / 'signalbridge_github_manual_refresh.py').read_text(encoding='utf-8')
        self.assertIn('ingest >> dispatch', dag_source)
        self.assertIn('stats.get("succeeded")', dag_source)
        self.assertIn('match_watch_events()', dag_source)
        self.assertIn('send_member_notifications()', dag_source)

    def test_member_webhook_missing_must_not_send(self):
        with patch.dict('os.environ', {'SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY': ''}):
            self.assertEqual(notifier.send_member_notifications(), 0)

    def test_discord_message_does_not_allow_mentions(self):
        message = notifier.message_for((
            2, '모든 릴리스', 'grafana/grafana', '@everyone release',
            'https://github.com/grafana/grafana/releases/tag/v13.2.4',
            'NEW', 1
        ))
        self.assertNotIn('@everyone', message)


if __name__ == '__main__':
    unittest.main()
