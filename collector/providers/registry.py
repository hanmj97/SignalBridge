"""Reviewable provider registry; registration is NOT permission to crawl."""
from dataclasses import dataclass


@dataclass(frozen=True)
class Provider:
    code: str
    label: str
    category: str
    access: str
    implementation: str
    reference: str


PROVIDERS: tuple[Provider, ...] = (
    Provider('youthcenter', '온통청년 청년정책', 'POLICY', 'approval_pending',
             'V2-4 preflight only; official mapping not verified', 'https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiGuide'),
    Provider('yongin_jobs', '용인시 채용정보', 'JOB', 'dev_auto_approval',
             'user confirmed active API key; recruitment-only LAB importer + paused Airflow DAG', 'https://www.data.go.kr/data/15097328/openapi.do'),
    Provider('saramin', '사람인 채용', 'JOB', 'manual_approval',
             'V2-6A read-only adapter; no public republishing until approved', 'https://oapi.saramin.co.kr/guide/info'),
    Provider('work24', '고용24', 'JOB', 'verify_official_access',
             'official jobs/open recruitment API found; separate key approval needed', 'https://www.work24.go.kr/'),
    Provider('jobkorea', '잡코리아', 'JOB', 'manual_approval_public_education_priority',
             'official hiring + starter API exists; public institutions/schools prioritized', 'https://www.jobkorea.co.kr/service/api'),
    Provider('wanted', '원티드', 'JOB', 'partner_or_api_permission_needed',
             'no automatic site crawling; no API assumed', 'https://www.wanted.co.kr/'),
    Provider('jasoseol', '자소설닷컴', 'JOB', 'partner_or_api_permission_needed',
             'no automatic site crawling; no API assumed', 'https://jasoseol.com/'),
)


def provider(code: str) -> Provider:
    return next(item for item in PROVIDERS if item.code == code)
