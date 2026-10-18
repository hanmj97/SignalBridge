import unittest
from unittest.mock import MagicMock, patch
from collector.register_signalbridge_source import (
    SOURCE_KEY, repo_from_remote, validate_repo, verify_api_access, main,
)


class RegisterSignalBridgeSourceTests(unittest.TestCase):
    def test_https_origin(self):
        self.assertEqual("owner/SignalBridge", repo_from_remote("https://github.com/owner/SignalBridge.git"))

    def test_ssh_origin(self):
        self.assertEqual("owner/SignalBridge", repo_from_remote("git@github.com:owner/SignalBridge.git"))

    def test_ssh_url(self):
        self.assertEqual("owner/SignalBridge", repo_from_remote("ssh://git@github.com/owner/SignalBridge.git"))

    def test_reject_other_hosts(self):
        with self.assertRaises(ValueError): repo_from_remote("https://gitlab.com/owner/SignalBridge.git")

    def test_reject_credentials(self):
        with self.assertRaises(ValueError): repo_from_remote("https://username:secret@github.com/owner/repo.git")

    def test_reject_sneaky_paths(self):
        for s in ["owner/../repo", "owner/repo/subpath", "owner", "owner/repo?foo=bar", "owner/repo.git"]:
            with self.subTest(s=s), self.assertRaises(ValueError): validate_repo(s)

    def test_preview_does_not_touch_db_or_api(self):
        with patch('collector.register_signalbridge_source.verify_api_access') as api, \
             patch('collector.register_signalbridge_source.register') as register:
            self.assertEqual(0, main(['--repo', 'owner/SignalBridge']))
            api.assert_not_called()
            register.assert_not_called()

    @patch('collector.register_signalbridge_source.requests.get')
    def test_api_access_valid(self, get):
        response = MagicMock(status_code=200)
        response.json.return_value = []
        get.return_value = response
        self.assertEqual(0, verify_api_access('owner/SignalBridge'))
        self.assertEqual('https://api.github.com/repos/owner/SignalBridge/releases', get.call_args.args[0])

    @patch('collector.register_signalbridge_source.requests.get')
    def test_api_access_404_denied(self, get):
        get.return_value = MagicMock(status_code=404)
        with self.assertRaises(RuntimeError): verify_api_access('owner/SignalBridge')

    @patch('collector.register_signalbridge_source.requests.get')
    def test_api_wrong_json(self, get):
        response = MagicMock(status_code=200)
        response.json.return_value = {'message':'error'}
        get.return_value = response
        with self.assertRaises(RuntimeError): verify_api_access('owner/SignalBridge')

    @patch('collector.register_signalbridge_source.register', return_value='ADDED')
    @patch('collector.register_signalbridge_source.verify_api_access', return_value=1)
    def test_apply_uses_preflight_and_registers(self, api, register):
        self.assertEqual(0, main(['--repo', 'owner/SignalBridge', '--apply']))
        api.assert_called_once_with('owner/SignalBridge')
        register.assert_called_once_with('owner/SignalBridge')

    def test_source_key(self):
        self.assertEqual('github_signalbridge_self', SOURCE_KEY)


if __name__ == '__main__':
    unittest.main()
