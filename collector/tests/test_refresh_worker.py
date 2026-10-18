import unittest
from unittest.mock import MagicMock, patch

from collector.refresh_worker import claim_requests, finish_requests, run_pending


class RefreshWorkerTests(unittest.TestCase):
    def test_rejects_unbounded_batch(self):
        with self.assertRaises(ValueError):
            claim_requests(MagicMock(), 100)

    def test_empty_queue_does_not_call_source_collector(self):
        conn = MagicMock()
        conn.__enter__.return_value = conn
        with patch("collector.refresh_worker.connect", return_value=conn):
            with patch("collector.refresh_worker.claim_requests", return_value=[]):
                with patch("collector.refresh_worker.run_source") as collect:
                    result = run_pending()
        self.assertEqual(result, {"claimed": 0, "succeeded": 0, "failed": 0})
        collect.assert_not_called()

    def test_duplicate_member_requests_fetch_source_once(self):
        conn = MagicMock()
        conn.__enter__.return_value = conn
        cursor = conn.cursor.return_value.__enter__.return_value
        cursor.fetchone.return_value = (7, "Grafana", "grafana/grafana", None)
        with patch("collector.refresh_worker.connect", return_value=conn):
            with patch("collector.refresh_worker.claim_requests", return_value=[(10, 7), (11, 7)]):
                with patch("collector.refresh_worker.run_source", return_value=True) as collect:
                    with patch("collector.refresh_worker.finish_requests") as finish:
                        result = run_pending()
        self.assertEqual(result, {"claimed": 2, "succeeded": 2, "failed": 0})
        collect.assert_called_once()
        finish.assert_called_once_with(conn, [10, 11], True)

    def test_missing_unsubscribed_source_cannot_be_collected(self):
        conn = MagicMock()
        conn.__enter__.return_value = conn
        cursor = conn.cursor.return_value.__enter__.return_value
        cursor.fetchone.return_value = None
        with patch("collector.refresh_worker.connect", return_value=conn):
            with patch("collector.refresh_worker.claim_requests", return_value=[(10, 7)]):
                with patch("collector.refresh_worker.run_source") as collect:
                    with patch("collector.refresh_worker.finish_requests") as finish:
                        result = run_pending()
        collect.assert_not_called()
        self.assertEqual(result["failed"], 1)
        finish.assert_called_once_with(conn, [10], False)


if __name__ == "__main__":
    unittest.main()
