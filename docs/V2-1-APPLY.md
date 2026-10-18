# SignalBridge V2-1 — 정책·채용 데이터 공통 스키마 + 가상 데모 수집

> **적용 대상:** 기존 `D:\Projects\SignalBridge` 프로젝트 (브랜치 `feature/youth-policy-platform`). 새로운 프로젝트를 만들지 마세요.
>
> **범위:** 신규 파일만 추가합니다. 기존 릴리스 수집기/인증/Discord/Airflow/React 소스는 변경하지 않습니다.
>
> **주의:** 온통청년 API 승인을 아직 받지 않았으므로 실데이터는 **수집하지 않습니다**. 제공된 자료 3건은 명백하게 가상 데이터로 표시되어 있고, `is_demo=true`로 저장됩니다.

## 0. 패치 구성

- `db/migrations/015_opportunity_details.sql`: 기존 `records`·`record_versions`를 유지하는 신규 상세 검색 테이블
- `collector/opportunity_logic.py`: 공급처 독립적인 정규화 및 값 검증
- `collector/opportunity_store.py`: 기존 `records`, `record_versions`, `ingestion_runs`에 동일한 방식으로 저장·이력 생성
- `collector/opportunity_import.py`: 명시적인 `--write-demo` 옵션으로만 가상 데이터를 DB에 저장하는 CLI
- `collector/fixtures/*.json`: 가상 정책/지원금/채용 3건, 변경 사례 1건
- `collector/tests/test_opportunity_logic.py`: 부작용 없는 정규화·변경 감지 테스트
- `scripts/check-v2-1.ps1`: 기존 및 신규 수집기 테스트 + DB 접근 없는 dry-run
- `docs/V2-1-DB-VERIFY.sql`: SQL 조회 검증

## 1. 프로젝트와 DB 백업

PowerShell (기존 SignalBridge 폴더 사용):

```powershell
# 기존 프로젝트로 이동
Set-Location 'D:\Projects\SignalBridge'

# 실제 현재 Git 브랜치 확인 (V2용 브랜치 권장)
git branch --show-current

# 소스 백업은 이전 V1 Git 커밋에 보존되어 있음
# DB를 수정하기 전에 백업을 새로 만듦
.\scripts\phase15-backup.ps1
```

## 2. ZIP 파일 적용

PowerShell 다운로드 폴더에 `SignalBridge-V2-1-Opportunity-Foundation.zip` 저장 후:

```powershell
# ZIP에서 새 파일만 기존 프로젝트에 추가
Expand-Archive -Path "$HOME\Downloads\SignalBridge-V2-1-Opportunity-Foundation.zip" `
  -DestinationPath 'D:\Projects\SignalBridge' -Force

# 기존 Python 수집기와 신규 정규화 로직의 테스트 실행 (DB 쓰기 없음)
.\scripts\check-v2-1.ps1
```

마지막에 `SignalBridge V2-1 dry-run checks PASSED`가 표시되어야 함.

## 3. 별도 실습 DB 준비 및 마이그레이션 (기존 DB 보존)

기존 `signalbridge` DB에는 테스트 데이터를 넣지 않습니다. 테스트는 **`signalbridge_v2_lab`** 데이터베이스에서 진행합니다.

```powershell
# 별도 실습 DB 존재 여부 확인
$found = docker exec signalbridge-postgres psql -U signalbridge -d postgres -tAc `
  "SELECT 1 FROM pg_database WHERE datname = 'signalbridge_v2_lab'"
if ($LASTEXITCODE -ne 0) { throw 'Database lookup failed' }

# 미존재 시에만 실습 DB 생성 (기존 signalbridge DB 유지)
if ("$found".Trim() -ne '1') {
  docker exec signalbridge-postgres createdb -U signalbridge -O signalbridge signalbridge_v2_lab
  if ($LASTEXITCODE -ne 0) { throw 'Lab DB creation failed' }
}

# 기존 공통 수집 스키마 + 새로운 정책/채용 상세 스키마를 실습 DB에 생성
# 마이그레이션 적용 전 신규 테이블 검증용
& docker cp '.\db\init\001_schema.sql' `
  'signalbridge-postgres:/tmp/v2_lab_001_schema.sql'
if ($LASTEXITCODE -ne 0) { throw 'Base schema copy failed' }
& docker cp '.\db\migrations\015_opportunity_details.sql' `
  'signalbridge-postgres:/tmp/v2_lab_015_opportunity_details.sql'
if ($LASTEXITCODE -ne 0) { throw 'V2 schema copy failed' }

# 기존 테이블 구조를 참고해 실습 DB 초기화
& docker exec signalbridge-postgres psql -X -v ON_ERROR_STOP=1 `
  -U signalbridge -d signalbridge_v2_lab -f /tmp/v2_lab_001_schema.sql
if ($LASTEXITCODE -ne 0) { throw 'Lab base schema failed' }

# 정책/채용 상세 정보용 테이블 및 인덱스 생성
& docker exec signalbridge-postgres psql -X -v ON_ERROR_STOP=1 `
  -U signalbridge -d signalbridge_v2_lab -f /tmp/v2_lab_015_opportunity_details.sql
if ($LASTEXITCODE -ne 0) { throw 'V2-1 lab migration failed' }
```

나중에 실습 DB에서 데이터 흐름을 충분히 검증한 후에만 실제 `signalbridge` DB에 `015_opportunity_details.sql`을 적용합니다. **이번 단계에서 메인 DB를 변경하지 않아도 됩니다.**

## 4. 데모 데이터 적재 (원하면 수행)

아래 명령은 명시적으로 실행했을 때만 실제 DB에 `is_demo = true`로 **가상** 정책/지원금/채용 3건을 **실습 DB에만** 저장합니다.

```powershell
# 1회: NEW=3 기대 (처음 실행 시)
py -3 -m collector.opportunity_import `
  --fixture '.\collector\fixtures\opportunities_demo.json' --write-demo

# 2회: NEW=0, UPDATED=0, UNCHANGED=3 기대 (멱등성)
py -3 -m collector.opportunity_import `
  --fixture '.\collector\fixtures\opportunities_demo.json' --write-demo

# 변경 사례: UPDATED=1, UNCHANGED=2 기대
py -3 -m collector.opportunity_import `
  --fixture '.\collector\fixtures\opportunities_demo_updated.json' --write-demo

# 변경 데이터를 재실행: NEW=0, UPDATED=0, UNCHANGED=3 기대
py -3 -m collector.opportunity_import `
  --fixture '.\collector\fixtures\opportunities_demo_updated.json' --write-demo
```

`signalbridge_v2_lab` DB에만 삽입하므로 기존 `signalbridge` 운영 DB, GitHub 레코드 및 Discord 알림 이벤트와 충돌하지 않습니다. **아직 React에 표시되지 않습니다**. 공고 UI 전환은 V2-2입니다.

## 5. SQL 검증

`signalbridge_v2_lab` DB 연결을 선택한 뒤 `docs/V2-1-DB-VERIFY.sql`을 DBeaver에서 읽기 전용으로 실행하거나 아래 쿼리를 사용합니다:

```sql
SELECT r.external_id, r.title, r.current_version, d.kind, d.is_demo
FROM records r
JOIN opportunity_details d ON d.record_id=r.id
ORDER BY r.id DESC LIMIT 20;
```

## 6. 다음 단계와 데이터 품질 원칙

- 온통청년 인증키 승인 이후, 공식 API 응답 샘플의 **필드명/자료형/페이지 처리/오류 유형**을 확인하여 정규화 매핑을 구현합니다. API 키는 `.env`에만 보관하며 채팅/로그/Git에 공유하지 않습니다.
- 자료 제공처가 명시하지 않은 지역·연령·모집 상태는 추정하지 않고 `NULL`, `UNKNOWN`으로 유지합니다.
- 허가된 실데이터는 `is_demo=false`로 적재하되, 모의 데이터와 공식 공고를 API/UI에서 혼합 표시하지 않습니다.
- V2-2에서 통합 조회 API와 실제 서비스 메인/상세 UI를 추가하고, V2-3에서 스케줄 수집·운영 모니터링을 확장합니다.
- 공식 API 응답 구조 및 사용권이 아직 확인되지 않았으므로 *이번 패치는 라이브 온통청년 연동을 완료하지 않았습니다*.

## 7. 롤백

실습 DB에서만 스키마를 만들므로 기존 `signalbridge` DB는 유지됩니다. 문제가 있다면 DB 마이그레이션 롤백을 즉시 실행하기보다 오류 로그를 확인하세요. 데모는 별도 `signalbridge_v2_lab` 데이터베이스에만 존재합니다. 메인 `signalbridge` DB를 수정하거나 삭제하지 마세요. 실습 DB를 정리할 때도 먼저 백업 여부와 사용 여부를 확인하세요.
