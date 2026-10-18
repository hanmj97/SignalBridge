"""Phase 9 local-only safety contract: never send another member's notification to the shared hook."""
from pathlib import Path
import ast
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'dags' / 'watch_notifier.py'
class Phase9PrivacyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SCRIPT.read_text(encoding='utf-8')
    def test_python_syntax(self):
        ast.parse(self.text)
    def test_member_events_are_excluded_from_shared_webhook(self):
        self.assertIn('AND w.user_id IS NULL', self.text)
    def test_new_rules_start_from_creation_date(self):
        self.assertIn('v.detected_at > w.created_at', self.text)
    def test_idempotent_match(self):
        self.assertIn('ON CONFLICT (rule_id, record_version_id) DO NOTHING', self.text)
