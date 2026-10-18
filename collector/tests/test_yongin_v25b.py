import unittest
from collector.yongin_v25b import to_canonical, normalize_batch, DATASET_URL, SOURCE_KEY
from collector.providers.yongin_jobs import JobPreview, preview

class YonginStorageContractTests(unittest.TestCase):
    def row(self, **changes):
        data=dict(source_id='123',title='채용 공고 예시',department='총무과',starts_on='2026-10-01',
            ends_on='2026-10-31',source_status='Y',source_type='채용정보')
        data.update(changes)
        return JobPreview(**data)
    def test_canonical_real_but_link_is_dataset(self):
        item=to_canonical(self.row())
        self.assertEqual(item['url'],DATASET_URL)
        self.assertFalse(item['is_demo'])
        self.assertEqual(item['external_id'],'yongin:123')
        self.assertEqual(item['status'],'UNKNOWN')
        self.assertEqual(item['region_codes'],[])
        self.assertIsNone(item['age_min'])
        self.assertIsNone(item['eligibility_summary'])
    def test_stable_for_recollection(self):
        from collector.logic import content_hash
        self.assertEqual(content_hash(to_canonical(self.row())),content_hash(to_canonical(self.row())))
    def test_changes_when_deadline_changes(self):
        from collector.logic import content_hash
        a=content_hash(to_canonical(self.row()))
        b=content_hash(to_canonical(self.row(ends_on='2026-11-15')))
        self.assertNotEqual(a,b)
    def test_duplicate_source_ids_rejected(self):
        with self.assertRaises(ValueError): normalize_batch([self.row(),self.row()])
    def test_provider_does_not_infer_job_region(self):
        item=to_canonical(self.row())
        self.assertEqual(item['region_codes'],[])
    def test_cli_rejects_non_explicit_network(self):
        from collector.yongin_v25b import main
        self.assertEqual(main(['--write-lab']),1)
    def test_missing_article_url_policy(self):
        self.assertEqual(SOURCE_KEY, 'yongin_jobs_official')
        self.assertEqual(to_canonical(self.row())['url'], DATASET_URL)

if __name__=='__main__':unittest.main()
