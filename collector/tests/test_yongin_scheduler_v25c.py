import unittest
from unittest.mock import Mock

from collector.yongin_scheduler_v25c import collect_once, main
from collector.providers.yongin_jobs import JobPreview, ProviderError


def job(n):
    return JobPreview(source_id=str(n), title='Official job ' + str(n), department='HR',
                      starts_on='2026-10-01', ends_on='2026-10-31',
                      source_status='Y', source_type='채용정보')


class YonginBatchTests(unittest.TestCase):
    def test_three_pages_collected_once_and_written_atomically(self):
        fetch = Mock(side_effect=[{'p': 1}, {'p': 2}, {'p': 3}])
        parse = Mock(side_effect=[[job(1)], [job(2)], [job(3)]])
        write = Mock(return_value=(3, 0, 0))
        outcome = collect_once('secret', fetch=fetch, parse=parse, write=write, persist=True)
        self.assertEqual((outcome.pages_requested, outcome.rows_collected, outcome.inserted), (3, 3, 3))
        write.assert_called_once()
        self.assertEqual(len(write.call_args.args[0]), 3)

    def test_preview_does_not_write(self):
        write = Mock()
        outcome = collect_once('secret', pages=1, fetch=lambda *_a, **_kw: {},
                               parse=lambda _doc: [job(1)], write=write)
        self.assertFalse(outcome.wrote_to_db)
        write.assert_not_called()

    def test_empty_after_filter_does_not_write(self):
        write = Mock()
        outcome = collect_once('secret', pages=2, fetch=lambda *_a, **_kw: {},
                               parse=lambda _doc: [], persist=True, write=write)
        self.assertEqual(outcome.rows_collected, 0)
        write.assert_not_called()

    def test_duplicate_ids_across_pages_rejected_before_write(self):
        write = Mock()
        with self.assertRaises(ProviderError):
            collect_once('secret', pages=2, fetch=lambda *_a, **_kw: {},
                         parse=lambda _doc: [job(1)], persist=True, write=write)
        write.assert_not_called()

    def test_bad_api_page_aborts_without_write(self):
        write = Mock()
        parse = Mock(side_effect=[[job(1)], ProviderError('invalid response')])
        with self.assertRaises(ProviderError):
            collect_once('secret', pages=2, fetch=lambda *_a, **_kw: {},
                         parse=parse, persist=True, write=write)
        write.assert_not_called()

    def test_invalid_bounds_fail_before_fetch(self):
        fetch = Mock()
        for pages, rows in [(0, 10), (11, 10), (1, 0), (1, 21)]:
            with self.assertRaises(ValueError):
                collect_once('secret', pages=pages, rows=rows, fetch=fetch)
        fetch.assert_not_called()

    def test_missing_key_rejected(self):
        with self.assertRaises(ValueError):
            collect_once('')

    def test_live_flag_required(self):
        self.assertEqual(main(['--write-lab']), 2)

    def test_write_errors_propagate(self):
        def broken(_):
            raise RuntimeError('write failed')
        with self.assertRaises(RuntimeError):
            collect_once('secret', pages=1, fetch=lambda *_a, **_kw: {},
                         parse=lambda _doc: [job(1)], persist=True, write=broken)


if __name__ == '__main__':
    unittest.main()
