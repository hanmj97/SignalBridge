import unittest
from collector.logic import changed_fields, content_hash, normalize_release


class TestCollectorLogic(unittest.TestCase):
    def setUp(self):
        self.release = {
            "id": 123, "tag_name": "v1.0.0", "name": "Version 1", "body": "Fixes",
            "html_url": "https://github.com/example/project/releases/tag/v1.0.0",
            "published_at": "2026-10-18T00:00:00Z", "prerelease": False,
            "draft": False, "assets": [{"download_count": 100}],
        }

    def test_repeated_payload_has_same_hash(self):
        a = normalize_release(self.release)
        b = normalize_release({**self.release, "assets": [{"download_count": 999}]})
        self.assertEqual(content_hash(a), content_hash(b))

    def test_meaningful_update_changes_hash(self):
        a = normalize_release(self.release)
        b = normalize_release({**self.release, "body": "Security fix"})
        self.assertNotEqual(content_hash(a), content_hash(b))
        self.assertEqual(changed_fields(a, b), ["body"])

    def test_unicode_and_fallback_title(self):
        a = normalize_release({**self.release, "name": "", "tag_name": "한글-v2"})
        self.assertEqual(a["title"], "한글-v2")
        self.assertEqual(len(content_hash(a)), 64)


if __name__ == "__main__":
    unittest.main()
