import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

from collector.youthcenter_v24 import (
    FeedError, parse_synthetic_feed, inspect_official_xml,
    fetch_official_xml, main, MAX_BYTES
)

FIXTURE = Path(__file__).resolve().parents[1] / 'fixtures' / 'youthcenter_synthetic_v24.xml'
FIXTURE_UPDATED = Path(__file__).resolve().parents[1] / 'fixtures' / 'youthcenter_synthetic_v24_updated.xml'

class YouthCenterPreflightTests(unittest.TestCase):
    def test_two_fake_records_normalize(self):
        data = parse_synthetic_feed(FIXTURE.read_bytes())
        self.assertEqual(len(data), 2)
        self.assertTrue(all(row['is_demo'] for row in data))
        self.assertEqual(data[0]['region_codes'], ['경기','서울'])
        self.assertEqual(data[1]['kind'], 'JOB')

    def test_update_only_one_meaningful_field(self):
        a,b = parse_synthetic_feed(FIXTURE.read_bytes()), parse_synthetic_feed(FIXTURE_UPDATED.read_bytes())
        self.assertNotEqual(a[0]['application_deadline'], b[0]['application_deadline'])
        self.assertEqual(a[1], b[1])

    def test_live_envelope_rejected_as_fixture(self):
        with self.assertRaises(FeedError):
            parse_synthetic_feed(b'<response><item /></response>')

    def test_xml_entities_rejected(self):
        with self.assertRaises(FeedError):
            parse_synthetic_feed(b'<!DOCTYPE a [<!ENTITY test "abc">]><a/>')

    def test_bad_xml_and_oversize_rejected(self):
        with self.assertRaises(FeedError):
            parse_synthetic_feed(b'<broken>')
        with self.assertRaises(FeedError):
            parse_synthetic_feed(b'x'*(MAX_BYTES+1))

    def test_duplicate_ids_rejected(self):
        text=FIXTURE.read_text(encoding='utf-8')
        text=text.replace('SB-V24-FAKE-JOB-102','SB-V24-FAKE-POLICY-101')
        with self.assertRaises(FeedError):
            parse_synthetic_feed(text.encode())

    def test_bad_data_is_rejected(self):
        text=FIXTURE.read_text(encoding='utf-8').replace('2026-12-10','2026-99-99')
        with self.assertRaises(FeedError):
            parse_synthetic_feed(text.encode())

    def test_introspection_only_returns_tags(self):
        x=inspect_official_xml(b'<result><secret>VERY_PRIVATE</secret></result>')
        self.assertEqual(x['root'],'result')
        self.assertNotIn('VERY_PRIVATE',str(x))

    @patch('requests.get')
    def test_fixed_host_and_no_redirects(self, getter):
        resp=MagicMock(status_code=200)
        resp.iter_content.return_value=iter([b'<ok/>'])
        getter.return_value=resp
        self.assertEqual(fetch_official_xml('fake-key',page=1),b'<ok/>')
        args,kwargs=getter.call_args
        self.assertEqual(args[0],'https://www.youthcenter.go.kr/opi/youthPlcyList.do')
        self.assertFalse(kwargs['allow_redirects'])
        self.assertEqual(kwargs['params']['openApiVlak'],'fake-key')

    @patch('requests.get')
    def test_http_error_not_leak_body_or_token(self, getter):
        getter.return_value=MagicMock(status_code=403)
        with self.assertRaises(FeedError) as ctx:
            fetch_official_xml('SUPER_SECRET')
        self.assertNotIn('SUPER_SECRET',str(ctx.exception))

    def test_normal_cli_dry_run(self):
        self.assertEqual(main(['--fixture',str(FIXTURE)]),0)

    def test_live_requires_explicit_flag(self):
        self.assertEqual(main(['--inspect-live']),1)

    def test_live_cannot_write_demo(self):
        self.assertEqual(main(['--inspect-live','--permit-network','--write-demo']),1)

if __name__ == '__main__':unittest.main()
