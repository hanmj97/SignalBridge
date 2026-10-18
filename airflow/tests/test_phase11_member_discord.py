from pathlib import Path
import ast
import base64
import importlib.util
import secrets
import unittest
from unittest.mock import patch
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

PATH = Path(__file__).resolve().parents[1] / 'dags' / 'watch_notifier.py'

class MemberDiscordTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('_p11_notifier', PATH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_python_compiles(self):
        ast.parse(PATH.read_text(encoding='utf-8'))

    def test_discord_encrypted_destination_decrypts(self):
        raw = 'https://discord.com/api/webhooks/' + '123456789012345678' + '/' + 'a' * 70
        key = secrets.token_bytes(32)
        iv = secrets.token_bytes(12)
        encrypted = 'v1:' + base64.b64encode(iv + AESGCM(key).encrypt(iv,raw.encode(),None)).decode()
        value = self.module._decode_member_hook(encrypted,base64.b64encode(key).decode())
        self.assertEqual(value,raw)

    def test_cannot_decrypt_with_wrong_key(self):
        raw = 'https://discord.com/api/webhooks/123456789012345678/' + 'x'*70
        key = secrets.token_bytes(32)
        iv = secrets.token_bytes(12)
        ciphertext = 'v1:' + base64.b64encode(iv+AESGCM(key).encrypt(iv,raw.encode(),None)).decode()
        with self.assertRaises(ValueError):
            self.module._decode_member_hook(ciphertext,base64.b64encode(secrets.token_bytes(32)).decode())

    def test_only_owner_channel_and_post_connection_events(self):
        sql=self.module.MEMBER_PENDING_SQL
        self.assertIn('c.user_id=w.user_id',sql)
        self.assertIn('e.created_at>c.activated_at',sql)
        self.assertIn('v.detected_at>c.activated_at',sql)
        self.assertIn("e.status='PENDING'",sql)

    def test_no_member_key_means_no_member_delivery(self):
        with patch.dict('os.environ',{'SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY':''}):
            self.assertEqual(self.module.send_member_notifications(),0)

    def test_discord_destination_cannot_point_to_unrelated_host(self):
        self.assertIsNone(self.module.DISCORD_URL_RE.fullmatch('https://example.com/api/webhooks/123/abc'))

if __name__ == '__main__': unittest.main()
