# SignalBridge V2-3 — Personal opportunity subscriptions + Discord integration

## 목적
기존 GitHub 릴리스/회원정보/Discord 연결/우측 상세 패널은 보존하면서, 회원별 정책·채용 관심 규칙과 별도의 변경 알림 파이프라인을 구축합니다.

- **회원 DB `signalbridge`**: `opportunity_alert_rules`, `opportunity_alert_events` (마이그레이션 016)
- **실습 DB `signalbridge_v2_lab`**: V2-1의 `opportunity_details`, `record_versions`; 읽기 전용으로만 접근
- **기존 Discord 연결**: `member_discord_channels`의 암호화된 웹훅 재사용
- **데모 정보**: `d.is_demo=FALSE` 제약으로 알림 큐에서 제외
- **구독한 이후에 수집한 변경만** 매칭; 기존 데이터를 소급 발송하지 않음
- **Airflow DAG**: `signalbridge_opportunity_alerts_30m`을 별도로 추가하며 최초 PAUSED. 기존 릴리스 DAG는 수정하지 않음.

## 1. 작업 전 DB·소스 백업
기존 `D:\Projects\SignalBridge`의 브랜치 `feature/youth-policy-platform`에서 실행합니다.

```powershell
# 프로젝트 폴더로 이동
Set-Location 'D:\Projects\SignalBridge'

# 현재 DB 개인 백업 생성 (기존 Phase 15 백업 스크립트)
.\scripts\phase15-backup.ps1

# V2-3 적용 전 파일별 백업
$backup = '.\backups\before-v2-3-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$files = @('frontend\src\App.tsx','frontend\src\api.ts','frontend\src\OpportunitiesPanel.tsx',
           'frontend\src\styles.css',
           'backend\src\main\java\dev\signalbridge\api\config\AccountSecurityConfig.java')
foreach ($file in $files) {
    $out = Join-Path $backup $file
    New-Item -ItemType Directory -Path (Split-Path $out -Parent) -Force | Out-Null
    Copy-Item $file $out -Force
}
```

## 2. 패치 압축 해제

```powershell
# ZIP 파일 경로는 다운로드 위치에 맞게 조정
Expand-Archive -Path "$HOME\Downloads\SignalBridge-V2-3-Opportunity-Alerts.zip" `
  -DestinationPath 'D:\Projects\SignalBridge' -Force
```

## 3. DB 마이그레이션 (중요: `signalbridge`, 실습 DB 아님)

```powershell
# SQL을 PostgreSQL 컨테이너로 복사
# 비파괴: 기존 계정, 릴리스, webhook, V2 실습 데이터를 변경하지 않음
docker cp '.\db\migrations\016_opportunity_alerts.sql' `
 'signalbridge-postgres:/tmp/016_opportunity_alerts.sql'
if ($LASTEXITCODE -ne 0) { throw 'Migration copy failed' }

# 본 운영용 계정 DB에 별도 V2 구독/이벤트 테이블 생성
docker exec signalbridge-postgres psql -X -v ON_ERROR_STOP=1 `
 -U signalbridge -d signalbridge -f /tmp/016_opportunity_alerts.sql
if ($LASTEXITCODE -ne 0) { throw 'Migration failed' }

# 신규 테이블 확인
docker exec signalbridge-postgres psql -U signalbridge -d signalbridge `
 -c '\dt opportunity_alert_*'
```

## 4. API / Frontend / Airflow 반영

```powershell
# 기존 Compose 파일 + V2-2 조회 설정 + V2-3 알림 설정
$compose = @(
 '-f','docker-compose.yml',
 '-f','docker-compose.phase2.yml',
 '-f','docker-compose.phase3.yml',
 '-f','docker-compose.search-experiment.yml',
 '-f','docker-compose.phase6.yml',
 '-f','docker-compose.phase10-email.yml',
 '-f','docker-compose.phase11-discord.yml',
 '-f','docker-compose.v2-2.yml',
 '-f','docker-compose.v2-3.yml'
)

# 환경변수 값은 출력하지 않고 Compose 구문 유효성만 확인
docker compose @compose config --quiet
if ($LASTEXITCODE -ne 0) {throw 'Compose invalid'}

# Spring Security 접근 규칙 + 신규 회원별 구독 API 빌드
docker compose @compose up -d --no-deps --build api
if ($LASTEXITCODE -ne 0) {throw 'API rebuild failed'}

# Airflow는 마운트된 새 DAG를 인식하도록 환경변수 반영하여 재생성
# DAG는 안전상 PAUSED로 생성되므로 실제 발송은 자동 시작되지 않음
docker compose @compose up -d --no-deps --force-recreate airflow
if ($LASTEXITCODE -ne 0) {throw 'Airflow start failed'}

# React 타입 검사와 정적 프런트엔드 빌드
docker compose @compose exec web npm run build
if ($LASTEXITCODE -ne 0) {throw 'React build failed'}

# 조회 중심의 비파괴 검사
.\scripts\check-v2-3.ps1
```

## 5. 화면 확인
1. `http://localhost:5173` > **청년정책·채용**.
2. 기존처럼 **우측 상세 패널**이 열리는지 확인.
3. 로그인 후 **내 정책·채용 관심 알림**에서 예: 키워드 `DBA`, 지역 `경기`, 유형 `채용공고`를 저장.
4. 비활성화/재활성화/삭제가 정상 동작하는지 확인.
5. 기존 **관심 알림**에서 Discord 연결이 유지되는지 확인.
6. 가상 데모 정책 3건은 알림 이벤트로 전송되지 않는 것이 정상.

## 6. DBeaver / Airflow 확인
회원 DB(`signalbridge`)에서만:

```sql
SELECT r.id, u.email, r.keyword, r.kind, r.region, r.age, r.enabled
FROM opportunity_alert_rules r JOIN app_users u ON u.id=r.user_id
ORDER BY r.id DESC;
SELECT id, rule_id, catalog_version_id, status, attempts, delivered_at
FROM opportunity_alert_events ORDER BY id DESC LIMIT 20;
```

Airflow 작업 확인:

```powershell
# 새로운 정책 알림 DAG 조회
# DAG는 기본적으로 일시 중지(PAUSED) 상태
docker compose @compose exec airflow airflow dags list | Select-String 'signalbridge_opportunity_alerts_30m'

# DAG 파일 import 오류 조회
docker compose @compose exec airflow airflow dags list-import-errors
```

Python 매칭 로직은 Windows의 `check-v2-3.ps1`에서 테스트합니다. 이 검사는 실제 Discord 메시지를 발송하지 않습니다.

### 아직 실데이터를 발송할 수 없는 이유
- 온통청년 등 공식 API 승인이 아직 완료되지 않았고, 현재 V2 데이터는 `is_demo=true`로만 적재되어 있습니다.
- 기존 회원 Discord 연결은 재사용하지만, Airflow V2 DAG는 기본 PAUSED이므로 **실제 비데모 수집 경로가 안정적으로 확인된 뒤 운영자가 직접 DAG를 켜야** 합니다.
- 현재 변경 조회는 최근 7일을 검사합니다. 7일 이상의 장기 중단에는 별도 백필이 필요합니다.
- 채용 지역이나 연령이 누락되면 충족한다고 간주하지 않습니다. 지원 자격은 사용자가 원문에서 최종 확인해야 합니다.
- 실제 외부 공개 전 계정/로그 보안, API 제한, webhook 연결을 재검증해야 합니다.
