import importlib.util
import pathlib
import unittest
from unittest.mock import Mock, patch

PATH = pathlib.Path(__file__).resolve().parents[1] / 'dags' / 'watch_notifier.py'
SPEC = importlib.util.spec_from_file_location('watch_notifier_p8', PATH)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)

class WatchNotifierTests(unittest.TestCase):
    def test_dag_wires_new_tasks(self):
        dag = (PATH.parent / 'signalbridge_collector.py').read_text(encoding='utf-8')
        self.assertIn('queue_watch_events', dag)
        self.assertIn('notify_watch_events', dag)
        self.assertIn('collect >> verify >> queue >> dispatch', dag)
    def test_notifications_are_idempotent(self):
        self.assertIn('ON CONFLICT (rule_id, record_version_id) DO NOTHING', module.MATCH_EVENTS_SQL)
    def test_only_after_rule_creation(self):
        self.assertIn('v.detected_at > w.created_at', module.MATCH_EVENTS_SQL)
    def test_matches_literal_substrings(self):
        self.assertIn('strpos(lower', module.MATCH_EVENTS_SQL)
        self.assertNotIn('ILIKE', module.MATCH_EVENTS_SQL)
    def test_webhook_not_configured(self):
        with patch.dict('os.environ', {'SIGNALBRIDGE_DISCORD_WEBHOOK_URL': ''}):
            self.assertEqual(module.send_watch_notifications(), 0)
    def test_no_unsafe_url_or_mentions(self):
        self.assertEqual(module._safe_url('https://evil.example/'), '')
        self.assertEqual(module._safe_url('https://github.com.evil.test/x'), '')
        message = module.message_for((1, '@everyone', 'Grafana', '`danger`', 'https://github.com/grafana/grafana/releases', 'NEW', 1))
        self.assertNotIn('@everyone', message)
        self.assertIn('https://github.com/grafana/grafana/releases', message)

if __name__ == '__main__': unittest.main()
