"""Regression tests for safe, read-only preflight behavior."""
import tempfile
import unittest
from pathlib import Path
from phase15_preflight import parse_env, inspect_env, tracked_sensitive_path, inspect_tracked


class PreflightTests(unittest.TestCase):
    def test_env_parser_ignores_comments_and_detects_duplicates(self):
        values, duplicates = parse_env('# RESEND_API_KEY=ignored\nA=one\nA=two\nB="hello"\n')
        self.assertNotIn('RESEND_API_KEY', values)
        self.assertEqual(values['A'], 'two')
        self.assertEqual(values['B'], 'hello')
        self.assertEqual(duplicates, {'A'})

    def test_duplicate_env_is_error_without_exposing_value(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / '.env'
            path.write_text('SIGNALBRIDGE_PUBLIC_URL=http://localhost:5173\nSIGNALBRIDGE_PUBLIC_URL=another\n', encoding='utf-8')
            errors, _ = inspect_env(Path(tmp))
            self.assertTrue(any('Duplicate' in e for e in errors))
            self.assertNotIn('another', repr(errors))

    def test_malformed_appended_env_key_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / '.env'
            path.write_text('SIGNALBRIDGE_PUBLIC_URL=http://localhost:5173SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY=x\n', encoding='utf-8')
            errors, _ = inspect_env(Path(tmp))
            self.assertTrue(any('PUBLIC_URL' in e for e in errors))

    def test_sensitive_git_paths(self):
        for name in ('.env', '.env.backup-123', 'backups/a.dump', 'frontend/node_modules/a.js', 'file.p12'):
            self.assertTrue(tracked_sensitive_path(name), name)
        for name in ('.env.example', 'backend/src/main/App.java', 'README.md'):
            self.assertFalse(tracked_sensitive_path(name), name)

    def test_secret_scan_never_prints_actual_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            secret = 're_' + 'abcdef0123456789abcdef0123456789'
            (folder / 'secret.py').write_text('x = "' + secret + '"\n', encoding='utf-8')
            errors, _ = inspect_tracked(folder, ['secret.py'])
            self.assertEqual(len(errors), 1)
            self.assertNotIn(secret, repr(errors))

    def test_secret_value_base64_invalid(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / '.env').write_text('SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY=bad-value\n', encoding='utf-8')
            errors, _ = inspect_env(Path(tmp))
            self.assertTrue(any('Base64' in e for e in errors))


if __name__ == '__main__':
    unittest.main()
