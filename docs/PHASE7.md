# SignalBridge Phase 7: 수집 운영 기록을 웹페이지에 표시

## 기존 상태
- Phase 1 PostgreSQL `sources` / `ingestion_runs` 보유
- Phase 5.5 Spring Boot OR/UNION 전환 지원
- Phase 6 Airflow 30분 수집 DAG 실행 성공
- React Motion V2.4 (450ms Fade Up + Stagger) 및 릴리스 버전 이력 적용

## 이 패치가 변경하는 항목
- **추가**: `backend/src/main/java/dev/signalbridge/api/operations/*.java` 4개
- **추가**: `backend/src/test/java/dev/signalbridge/api/operations/IngestionServiceTest.java`
- **추가**: `frontend/src/OperationsPanel.tsx`
- **추가**: `scripts/check-phase7.ps1`
- **교체**: `frontend/src/App.tsx`, `frontend/src/api.ts`, `frontend/src/styles.css`
- **유지**: `CatalogRepository.java`, Phase 5.5 검색 모드, DB 볼륨, Airflow DAG / Compose 파일, UI 모션
- **SQL 마이그레이션 없음** / 수집 관련 수정 API 없음

## 1. 기존 파일 백업 (PowerShell)
```powershell
# 기존 코드 경로로 이동
Set-Location 'D:\Projects\SignalBridge'

# frontend 변경 파일 3개를 폴더에 백업
New-Item -ItemType Directory -Force '.\backups\before-phase7' | Out-Null
Copy-Item '.\frontend\src\App.tsx' '.\backups\before-phase7\App.tsx' -Force
Copy-Item '.\frontend\src\api.ts' '.\backups\before-phase7\api.ts' -Force
Copy-Item '.\frontend\src\styles.css' '.\backups\before-phase7\styles.css' -Force
```

## 2. ZIP 압축 해제
```powershell
# 받은 ZIP을 프로젝트 루트에 덮어쓰기 (이전 백업 완료 후 진행)
Expand-Archive -Path "$HOME\Downloads\SignalBridge-Phase7-Ingestion-Monitoring.zip" `
  -DestinationPath 'D:\Projects\SignalBridge' -Force

# 새로운 백엔드 모듈 확인
Get-ChildItem '.\backend\src\main\java\dev\signalbridge\api\operations'
```

## 3. API 컨테이너만 재빌드
사용자가 Phase 5.5 및 Phase 6 때 사용한 Compose 파일을 함께 나열한다. 아래 명령은 기존 PostgreSQL **컨테이너/볼륨 재생성 없이** API만 재빌드한다. search-experiment는 원래 OR 설정이 기본값이다.

```powershell
# 기존 DB와 Airflow는 변경하지 않고 API의 Java 코드와 테스트를 빌드
$compose = @('-f','docker-compose.yml','-f','docker-compose.phase2.yml', '-f','docker-compose.phase3.yml', '-f','docker-compose.search-experiment.yml', '-f','docker-compose.phase6.yml')
docker compose @compose config --quiet
docker compose @compose up -d --no-deps --build api

# 애플리케이션 시작 및 빌드 로그
# 주의: 로그에 자체 설정/환경 변수 등이 포함될 수 있어 공유 전에 확인 필요
docker compose @compose logs --tail=70 api
```

API가 `8081`에 매핑된 경우 `http://localhost:8081/api/v1/operations/ingestions`를 사용한다. 로컬 포트가 다르면 `docker compose @compose ps`로 포트 확인 후 교체.

## 4. 검증
```powershell
# 운영 기록 API 정상 응답 / 상태값 / 노출 컬럼 검증
.\scripts\check-phase7.ps1

# React TypeScript 빌드 (해당 Docker web 컨테이너가 실행 중인 경우)
docker compose @compose exec web npm run build
```

브라우저 `http://localhost:5173` > **시스템 상태** 메뉴로 이동. 24시간 수집 건수, 소스별 최신 기록, 최근 작업 12개가 DB 값과 일치하는지 확인한다. **대시보드**의 시스템 상태 카드 하단에도 24시간 요약이 표시된다. 상단 새로고침 버튼, 화면 활성 중 60초 간격 재조회도 확인한다.

## 백엔드 API 구조
```
GET /api/v1/operations/ingestions
Controller → IngestionService → IngestionRepository → PostgreSQL
                                                 ├ sources LEFT JOIN LATERAL (latest ingestion_runs)
                                                 ├ 24-hour summary
                                                 └ recent 12 ingestion_runs
```

`latestStatus`가 `NOT_MODIFIED`면 변경 없음이지만 **수집 정상 완료**로 집계된다. `RUNNING`은 완료에 포함하지 않는다. `FAILED`도 기록되고, 재시도 성공 전 실패 기록은 24시간 실패 수에 남는다. 단일 Airflow DAG 실행은 **소스별 3개의 `ingestion_runs`**를 생성하므로 이 수치는 DAG 실행 횟수가 아니다.

## 오류 처리와 의미
- `last_checked_at`은 **마지막 성공 확인 시각**이다. 오류가 난 뒤에도 직전 성공 시각이 남을 수 있다.
- 90분 넘는 미확인은 **'최근 확인 지연'**으로 표시한다. 스케줄러가 멈췄다는 증거는 아니다.
- Airflow 메타데이터(DAG state, task state, 로그)는 이 API가 조회하지 않는다.
- `ingestion_runs.error_message`, DB 접속정보, GitHub token, Discord webhook은 API 응답에 넣지 않는다.
- 이 페이지가 사용자에게 공개되는 경우 DB 실행 건수 자체가 노출되므로 운영 정보 공개 범위를 재검토하고 인증된 운영자 화면으로 옮기는 것이 좋다.

## 실제 SQL 확인 (DB 읽기 전용)
```sql
-- 최신 소스 수집 기록 12개 확인
SELECT s.label, ir.status, ir.started_at, ir.finished_at,
       ir.fetched_count, ir.new_count, ir.updated_count
FROM ingestion_runs ir JOIN sources s ON s.id = ir.source_id
WHERE s.enabled = TRUE AND s.provider = 'github_releases'
ORDER BY ir.started_at DESC, ir.id DESC
LIMIT 12;
```

## 문제 발생 시 되돌리기
```powershell
# 변경된 프론트엔드 파일 3개 복원
Copy-Item '.\backups\before-phase7\App.tsx' '.\frontend\src\App.tsx' -Force
Copy-Item '.\backups\before-phase7\api.ts' '.\frontend\src\api.ts' -Force
Copy-Item '.\backups\before-phase7\styles.css' '.\frontend\src\styles.css' -Force

# 서버: 추가된 operations 패키지의 Java 파일을 삭제하고 API를 재빌드
# 기존 Catalog* 검색 기능 및 Airflow DAG는 변경하지 않는다.
```
- 최신 `RUNNING` 행의 시작 시각이 15분을 넘으면 '수집 실행 지연'을 표시한다. 이것 역시 Airflow DAG의 실제 상태를 보증하는 값은 아니다.
