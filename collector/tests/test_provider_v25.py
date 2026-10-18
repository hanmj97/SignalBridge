"""All tests are offline and do not read real credentials."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from collector.providers.registry import PROVIDERS, provider
from collector.providers.yongin_jobs import ProviderError, fetch_live, preview
from collector.provider_v25 import main

FIXTURE = Path(__file__).resolve().parents[1]/'fixtures/yongin_synthetic_v25.json'


class ProviderV25Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.doc = json.loads(FIXTURE.read_text(encoding='utf-8'))

    def test_unique_provider_codes(self):
        codes=[p.code for p in PROVIDERS]
        self.assertEqual(len(codes),len(set(codes)))
        self.assertEqual(provider('yongin_jobs').access, 'dev_auto_approval')

    def test_demo_two_recruitment_rows(self):
        found=preview(self.doc)
        self.assertEqual(len(found),2)
        self.assertEqual(found[0].ends_on,'2026-10-31')
        self.assertFalse(any(x.ready_for_persistence for x in found))

    def test_duplicate_source_article_rejected(self):
        doc=json.loads(json.dumps(self.doc))
        doc['response']['body']['items']['item'][1]['no']='SB-V25-FAKE-001'
        with self.assertRaises(ProviderError): preview(doc)

    def test_missing_title_fails(self):
        doc=json.loads(json.dumps(self.doc))
        del doc['response']['body']['items']['item'][0]['sj']
        with self.assertRaises(ProviderError): preview(doc)

    def test_api_error_not_treated_as_empty(self):
        doc=json.loads(json.dumps(self.doc))
        doc['response']['header']['resultCode']='20'
        with self.assertRaises(ProviderError): preview(doc)

    def test_date_order(self):
        doc=json.loads(json.dumps(self.doc))
        doc['response']['body']['items']['item'][0]['end_ymd']='20260901'
        with self.assertRaises(ProviderError): preview(doc)

    def test_no_key_rejected_without_network(self):
        with self.assertRaises(ProviderError): fetch_live('')

    def test_live_requires_explicit_opt_in(self):
        self.assertEqual(main(['--live-yongin']), 1)

    def test_offline_cli(self):
        self.assertEqual(main(['--fixture-yongin',str(FIXTURE)]),0)

    def test_registry_cli(self):
        self.assertEqual(main(['--list']),0)

    def test_transport_errors_hide_secrets(self):
        import requests
        secret='YOUR_SECRET_SENTINEL'
        with patch('requests.get',side_effect=requests.RequestException('URL contains '+secret)):
            with self.assertRaises(ProviderError) as cm:
                fetch_live(secret)
        self.assertNotIn(secret,str(cm.exception))


if __name__=='__main__': unittest.main()
