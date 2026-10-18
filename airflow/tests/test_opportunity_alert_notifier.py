import ast
import importlib.util
from datetime import datetime, timezone
from pathlib import Path
import unittest

P=Path(__file__).resolve().parents[1]/'dags'/'opportunity_alert_notifier.py'
spec=importlib.util.spec_from_file_location('v2_alert_notifier',P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
class OpportunityAlertTests(unittest.TestCase):
    def rule(self,keyword=None,kind=None,region=None,age=None):
        return (1,keyword,kind,region,age,datetime(2026,10,1,tzinfo=timezone.utc))
    def item(self,kind='JOB',regions=('경기',),age_min=19,age_max=34,source='공공기관'):
        return (10,20,'IT 개발자 채용',source,'NEW',datetime.now(timezone.utc),kind,list(regions),age_min,age_max,'기관','IT')
    def test_kind_region_and_age_match(self):
        self.assertTrue(m.matches(self.rule('IT','JOB','경기',30),self.item()))
    def test_wrong_region(self):
        self.assertFalse(m.matches(self.rule(region='서울'),self.item()))
    def test_nationwide_matches_specific_region(self):
        self.assertTrue(m.matches(self.rule(region='서울'),self.item(regions=('전국',))))
    def test_unknown_age_is_not_assumed_eligible(self):
        self.assertFalse(m.matches(self.rule(age=25),self.item(age_min=None)))
    def test_demo_rows_excluded_at_sql_level(self):
        self.assertIn('d.is_demo=FALSE',m.CHANGES_SQL)
    def test_no_old_changes_after_rule_creation(self):
        self.assertIn('detected <= rule[5]',P.read_text(encoding='utf-8'))
    def test_owner_channel_is_enforced(self):
        self.assertIn('c.user_id=r.user_id',m.PENDING_SQL)
    def test_message_prevents_discord_ping(self):
        self.assertNotIn('@everyone',m.format_message((1,'@everyone','agency','NEW',10,'')))
    def test_syntax(self):
        ast.parse(P.read_text(encoding='utf-8'))
        ast.parse((P.parent/'signalbridge_opportunity_alerts.py').read_text(encoding='utf-8'))
if __name__=='__main__':unittest.main()
