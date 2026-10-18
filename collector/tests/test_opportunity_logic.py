"""No database, network, API token, or external packages required."""
import unittest
from pathlib import Path

from collector.logic import changed_fields, content_hash
from collector.opportunity_import import load_fixture
from collector.opportunity_logic import normalize_opportunity

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "fixtures" / "opportunities_demo.json"
UPDATED = ROOT / "fixtures" / "opportunities_demo_updated.json"


class OpportunityLogicTests(unittest.TestCase):
    def setUp(self):
        self.first = load_fixture(FIXTURE)[0]

    def test_three_synthetic_records_normalize(self):
        rows = load_fixture(FIXTURE)
        self.assertEqual(len(rows), 3)
        self.assertTrue(all(row["is_demo"] for row in rows))
        self.assertEqual({row["kind"] for row in rows}, {"POLICY", "BENEFIT", "JOB"})

    def test_idempotent_hash_for_same_canonical_payload(self):
        b = dict(self.first)
        b["region_codes"] = sorted(b["region_codes"])
        self.assertEqual(content_hash(self.first), content_hash(b))

    def test_changed_field_is_precise(self):
        after = load_fixture(UPDATED)[0]
        self.assertEqual(changed_fields(self.first, after), ["benefit_summary"])
        self.assertNotEqual(content_hash(self.first), content_hash(after))

    def test_region_order_and_duplicates_do_not_change_digest(self):
        raw = {
            "external_id": "x", "title": "테스트", "url": "https://example.org/a",
            "kind": "POLICY", "region_codes": ["서울", "경기", "서울"]
        }
        first = normalize_opportunity(raw, is_demo=True)
        second = normalize_opportunity({**raw, "region_codes": ["경기", "서울"]}, is_demo=True)
        self.assertEqual(content_hash(first), content_hash(second))

    def test_no_inferred_eligibility_or_status(self):
        row = normalize_opportunity({
            "external_id": "y", "title": "테스트", "url": "https://example.org/b", "kind": "JOB"
        }, is_demo=True)
        self.assertEqual(row["status"], "UNKNOWN")
        self.assertIsNone(row["age_min"])
        self.assertIsNone(row["application_deadline"])

    def test_reject_invalid_url(self):
        raw = {"external_id": "t", "title": "t", "url": "http://localhost", "kind": "POLICY"}
        with self.assertRaises(ValueError):
            normalize_opportunity(raw, is_demo=True)

    def test_reject_reversed_dates(self):
        raw = {"external_id": "t", "title": "t", "url": "https://example.org/", "kind": "POLICY",
               "application_start": "2026-12-31", "application_deadline": "2026-01-01"}
        with self.assertRaises(ValueError):
            normalize_opportunity(raw, is_demo=True)

    def test_reject_bad_ages(self):
        raw = {"external_id": "t", "title": "t", "url": "https://example.org/", "kind": "POLICY",
               "age_min": 41, "age_max": 29}
        with self.assertRaises(ValueError):
            normalize_opportunity(raw, is_demo=True)

    def test_invalid_xml_or_provider_response_is_not_accepted_as_fixture(self):
        # Phase V2-1 expects our documented JSON contract, not unverified live XML fields.
        import tempfile
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "data.json"
            path.write_text('<xml><policy/></xml>', encoding="utf-8")
            with self.assertRaises(ValueError):
                load_fixture(path)

    def test_duplicate_external_ids_rejected(self):
        import json, tempfile
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "data.json"
            source = [
                {"external_id": "a", "title": "A", "url": "https://example.org/a", "kind": "POLICY"},
                {"external_id": "a", "title": "B", "url": "https://example.org/b", "kind": "JOB"},
            ]
            path.write_text(json.dumps(source), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate external_id"):
                load_fixture(path)


if __name__ == "__main__":
    unittest.main()
