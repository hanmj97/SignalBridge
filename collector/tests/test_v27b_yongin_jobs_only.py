"""Offline safety/contract tests for approved Yongin recruitment-only display."""
from pathlib import Path
import json
import unittest
from unittest.mock import MagicMock, patch

from collector.providers.yongin_jobs import fetch_live, preview, JobPreview
from collector.yongin_v25b import normalize_batch, to_canonical, LAB_DB, SOURCE_KEY

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / 'collector/fixtures/yongin_synthetic_v25.json'
REPO = ROOT / 'backend/src/main/java/dev/signalbridge/api/opportunities/OpportunityRepository.java'


class V27BRecruitmentOnlyTests(unittest.TestCase):
    def test_mixed_source_fixture_keeps_only_recruitment_not_exams(self):
        doc = json.loads(FIXTURE.read_text(encoding='utf-8'))
        rows = preview(doc)
        self.assertEqual({r.source_type for r in rows}, {'채용정보', '시험 정보'})
        saved = normalize_batch(rows)
        self.assertEqual(len(saved), 1)
        self.assertEqual(saved[0]['category'], '채용정보')
        self.assertFalse(saved[0]['is_demo'])
        self.assertEqual(saved[0]['kind'], 'JOB')

    def test_exam_cannot_be_directly_mapped_as_job(self):
        row = JobPreview(source_id='exam-1', title='일반 시험 일정', department='x',
                         starts_on=None, ends_on=None, source_status=None, source_type='시험정보')
        with self.assertRaises(ValueError):
            to_canonical(row)

    def test_api_requests_official_type_parameter(self):
        response = MagicMock(status_code=200)
        response.iter_content.return_value = iter([b'{"response":{"header":{"resultCode":"0"},"body":{"items":{"item":[]}}}}'])
        with patch('requests.get', return_value=response) as call:
            fetch_live('synthetic-test-key', start='2026-10-01', end='2026-10-15')
        self.assertEqual(call.call_args.kwargs['params']['type'], '채용정보')
        self.assertFalse(call.call_args.kwargs['allow_redirects'])
        response.close.assert_called_once()

    def test_public_catalog_filters_by_source_type_and_link_kind(self):
        java = REPO.read_text(encoding='utf-8')
        scope = java.split('private static final String CATALOG_SCOPE_GUARD',1)[1].split('private static final String COLUMNS',1)[0]
        self.assertIn("s.source_key='yongin_jobs_official'", scope)
        self.assertIn("d.category='채용정보'", scope)
        self.assertIn("d.link_kind='DATASET'", scope)
        self.assertIn('d.is_demo=FALSE', scope)
        self.assertIn('CATALOG_SCOPE_GUARD',java.split('public Optional<OpportunityDetail>',1)[1])
        self.assertIn('youthcenter_policies_official',scope)
        self.assertNotIn('bizinfo',scope)

    def test_lab_only_and_no_secret_values_in_source(self):
        importer = (ROOT/'collector/yongin_v25b.py').read_text(encoding='utf-8')
        self.assertEqual(LAB_DB, 'signalbridge_v2_lab')
        self.assertEqual(SOURCE_KEY, 'yongin_jobs_official')
        self.assertIn('current_database()', importer)
        self.assertIn("if row.source_type == '채용정보'",importer)
        self.assertIn("link_kind='DATASET'", importer)

    def test_already_persisted_exam_records_remain_hidden(self):
        sql = REPO.read_text(encoding='utf-8')
        self.assertIn("d.category='채용정보'", sql)
        self.assertIn('private static final String CATALOG_SCOPE_GUARD', sql)
        self.assertIn('new StringBuilder(" WHERE 1=1 ").append(CATALOG_SCOPE_GUARD)', sql)
        self.assertIn(' + CATALOG_SCOPE_GUARD', sql)  # Detail endpoint

if __name__ == '__main__':
    unittest.main()
