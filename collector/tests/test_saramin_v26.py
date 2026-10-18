"""No network, database, or provider secrets involved in these tests."""
from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
import unittest

from collector.providers.saramin_jobs import SaraminError, parse_jobs
from collector.saramin_v26 import run

FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "saramin_synthetic_v26.json"


class SaraminReadOnlyTests(unittest.TestCase):
    def setUp(self):
        self.source = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_synthetic_rows_are_normalized(self):
        rows = parse_jobs(self.source)
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0]["kind"], "JOB")
        self.assertEqual(rows[0]["region_codes"], ["서울"])
        self.assertIsNone(rows[0]["age_min"])
        self.assertEqual(rows[0]["organization"], "가상회사 A")

    def test_verified_http_provider_link_is_upgraded_and_tracking_removed(self):
        url = parse_jobs(self.source)[0]["url"]
        self.assertEqual(url, "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=987654321")

    def test_perpetual_recruitment_has_no_deadline(self):
        self.assertIsNone(parse_jobs(self.source)[1]["application_deadline"])

    def test_regular_job_has_deadline(self):
        self.assertRegex(parse_jobs(self.source)[0]["application_deadline"], r"^\d{4}-\d{2}-\d{2}$")

    def test_reject_malicious_host(self):
        doc=deepcopy(self.source)
        doc["jobs"]["job"][0]["url"] = "https://saramin.co.kr.evil.test/zf_user/jobs/relay/view?rec_idx=987654321"
        with self.assertRaises(SaraminError):parse_jobs(doc)

    def test_reject_mismatched_job_id(self):
        doc=deepcopy(self.source)
        doc["jobs"]["job"][0]["url"] = "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=111"
        with self.assertRaises(SaraminError):parse_jobs(doc)

    def test_reject_duplicate_id(self):
        doc=deepcopy(self.source)
        doc["jobs"]["job"].append(deepcopy(doc["jobs"]["job"][0]))
        with self.assertRaises(SaraminError):parse_jobs(doc)

    def test_reject_provider_error(self):
        with self.assertRaises(SaraminError):parse_jobs({"code":2,"message":"invalid key"})

    def test_reject_unexpected_envelope(self):
        with self.assertRaises(SaraminError):parse_jobs({"data":[]})

    def test_reject_oversized_batch(self):
        with self.assertRaises(SaraminError):parse_jobs({"jobs":{"job":[{}]*111}})

    def test_singleton_job(self):
        doc=deepcopy(self.source)
        doc["jobs"]["job"]=doc["jobs"]["job"][0]
        self.assertEqual(len(parse_jobs(doc)),1)

    def test_missing_optional_dates_are_unknown(self):
        row=parse_jobs(self.source)[1]
        self.assertIsNone(row["application_start"])
        self.assertIsNone(row["age_max"])

    def test_no_network_cli_fixture(self):
        self.assertEqual(run(["--fixture"]),0)

    def test_cli_live_requires_explicit_network(self):
        with self.assertRaises(SystemExit) as ctx:
            run(["--live"])
        self.assertEqual(ctx.exception.code,2)

    def test_cli_fixture_denies_network(self):
        with self.assertRaises(SystemExit) as ctx:
            run(["--fixture", "--permit-network"])
        self.assertEqual(ctx.exception.code,2)


if __name__=="__main__":unittest.main()
