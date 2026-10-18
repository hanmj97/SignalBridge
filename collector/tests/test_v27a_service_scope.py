"""Offline contract checks for the deliberately narrow V2-7A product scope.

These checks intentionally do not access HTTP, PostgreSQL, tokens or Discord.
"""
from pathlib import Path
import unittest

from collector.providers.registry import PROVIDERS, provider

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend/src/main/java/dev/signalbridge/api/opportunities"
FRONTEND = ROOT / "frontend/src"

class V27ATwoCategoryScopeTests(unittest.TestCase):
    def test_no_generic_company_grants_in_active_provider_registry(self):
        codes = {p.code for p in PROVIDERS}
        self.assertNotIn("bizinfo_support", codes)
        self.assertEqual(provider("saramin").category, "JOB")
        self.assertEqual(provider("jobkorea").reference,
                         "https://www.jobkorea.co.kr/service/api")

    def test_all_approved_for_future_consideration_but_no_unpermitted_crawlers(self):
        codes = [p.code for p in PROVIDERS]
        self.assertEqual(len(codes), len(set(codes)))
        for code in ("wanted", "jasoseol"):
            self.assertIn("permission_needed", provider(code).access)

    def test_catalog_guard_blocks_generic_grants_and_unrelated_yongin_exam_notices(self):
        src = (BACKEND / "OpportunityRepository.java").read_text(encoding="utf-8")
        guard = src.split('private static final String CATALOG_SCOPE_GUARD', 1)[1].split('private static final String COLUMNS', 1)[0]
        self.assertNotIn('bizinfo', guard)
        self.assertIn('yongin_jobs_official', guard)
        self.assertIn("d.category='채용정보'", guard)
        self.assertIn("d.link_kind='DATASET'", guard)
        self.assertIn("d.is_demo=FALSE", guard)
        self.assertIn('saramin_jobs_official', guard)
        self.assertIn('youthcenter_policies_official', guard)
        self.assertIn('d.kind IN', guard)
        self.assertIn('d.kind=\'JOB\'', guard)
        self.assertIn('where = new StringBuilder(" WHERE 1=1 ").append(CATALOG_SCOPE_GUARD)', src)
        self.assertIn(' + CATALOG_SCOPE_GUARD', src)  # detail protection

    def test_scoped_endpoint_is_backward_compatible(self):
        repo = (BACKEND / "OpportunityRepository.java").read_text(encoding="utf-8")
        controller = (BACKEND / "OpportunityController.java").read_text(encoding="utf-8")
        self.assertIn('return list(page, size, q, kind, region, includeDemo, null)', repo)
        self.assertIn('"invalid scope"', repo)
        self.assertIn('repository.list(page,size,q,kind,region,includeDemo,scope)', controller)

    def test_frontend_uses_only_two_top_level_categories(self):
        ui=(FRONTEND / "OpportunitiesPanel.tsx").read_text(encoding="utf-8")
        api=(FRONTEND / "api.ts").read_text(encoding="utf-8")
        self.assertIn("useState<'YOUTH'|'JOB'>('YOUTH')", ui)
        self.assertEqual(ui.count('aria-pressed={scope==='), 2)
        self.assertNotIn('<span>지원사업</span>', ui)
        self.assertIn('scope});', api)
        self.assertNotIn("if(kind)p.set('kind',kind)", api)
        self.assertIn('createPortal(', ui)

if __name__ == "__main__":
    unittest.main()
