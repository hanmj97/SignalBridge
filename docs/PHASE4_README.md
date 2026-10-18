# SignalBridge Phase 4 — 릴리스 변경 이력 타임라인

> **대상:** Phase 2 Spring Boot + Phase 3 React + Light V2.2 + Motion V2.4(450ms)를 이미 적용한 SignalBridge.
>
> **보존:** PostgreSQL 테이블/데이터, 기존 수집기, Docker Compose, React 레이아웃·색상·450ms 애니메이션.

## 목표

기존 목록의 릴리스를 선택하면 오른쪽 상세 패널에 해당 릴리스의 **최근 20개 수집 버전 이력**을 보여줍니다. `record_versions`의 실제 저장 기록만 출력하며, 발생하지 않은 변경을 만들어 표시하지 않습니다.

## 데이터 흐름

1. React에서 릴리스 제목 클릭 → `detailId` 설정
2. React `useEffect` 실행 → `fetchRecord(detailId)`와 `fetchRecordHistory(detailId)` 호출
3. Vite 프록시 → Spring Boot의 `GET /api/v1/records/{id}/history`
4. Controller → Service (ID 검증, 존재 여부 확인) → Repository
5. Repository → PostgreSQL `record_versions JOIN records JOIN sources`
6. JSON → React `recordHistory` 상태 → 상세 패널에 타임라인 렌더링

```
SELECT v.id, v.record_id, r.title AS record_title, s.label AS source_label,
       v.version, v.change_type, v.changed_fields, v.detected_at
FROM record_versions v
JOIN records r ON r.id = v.record_id
JOIN sources s ON s.id = r.source_id
WHERE v.record_id = :recordId
ORDER BY v.version DESC
LIMIT 20;
```

`record_versions`에는 기존 `UNIQUE (record_id, version)` 제약이 있어서, 이 조건과 정렬에 사용할 수 있는 B-tree 인덱스가 이미 있습니다. 테이블이 작을 때는 PostgreSQL 실행계획이 Seq Scan을 선택할 수도 있으니 실제 EXPLAIN으로 판단하세요.

## 신규 API

- `GET /api/v1/records/{id}/history` → `ChangeItem[]`
- 유효하지 않은 ID → 400
- 존재하지 않는 릴리스 ID → 404
- 정상 릴리스, 이력 없음 → `[]`
- 최신 버전부터 최대 20개를 반환

## 변경 파일 (총 7개)

```text
backend/src/main/java/dev/signalbridge/api/controller/CatalogController.java
backend/src/main/java/dev/signalbridge/api/service/CatalogService.java
backend/src/main/java/dev/signalbridge/api/repository/CatalogRepository.java
backend/src/test/java/dev/signalbridge/api/service/CatalogServiceTest.java
frontend/src/api.ts
frontend/src/App.tsx
frontend/src/styles.css
```

**다른 파일은 덮어쓰지 않습니다.** DB 마이그레이션도 없습니다.

## 적용 방법 (PowerShell)

```powershell
# SignalBridge 프로젝트로 이동
Set-Location 'D:\Projects\SignalBridge'

# 내려받은 ZIP을 별도 폴더에 압축 해제
$patch = Join-Path $HOME 'Downloads\SignalBridge-Phase4-Record-History'
Expand-Archive -Path (Join-Path $HOME 'Downloads\SignalBridge-Phase4-Record-History.zip') -DestinationPath $patch -Force

# 이번에 변경되는 7개 파일의 상대 경로
$files = @(
  'backend\src\main\java\dev\signalbridge\api\controller\CatalogController.java',
  'backend\src\main\java\dev\signalbridge\api\service\CatalogService.java',
  'backend\src\main\java\dev\signalbridge\api\repository\CatalogRepository.java',
  'backend\src\test\java\dev\signalbridge\api\service\CatalogServiceTest.java',
  'frontend\src\api.ts',
  'frontend\src\App.tsx',
  'frontend\src\styles.css'
)

# 현재 프로젝트 파일을 원래 폴더 구조 그대로 백업
$projectRoot = (Get-Location).Path
$backup = Join-Path $projectRoot 'backups\before-phase4'
foreach ($file in $files) {
  $from = Join-Path $projectRoot $file
  $to = Join-Path $backup $file
  New-Item -ItemType Directory -Path (Split-Path $to -Parent) -Force | Out-Null
  Copy-Item -Path $from -Destination $to -Force
}

# 새 파일 7개만 덮어쓰기
foreach ($file in $files) {
  Copy-Item -Path (Join-Path $patch $file) -Destination (Join-Path $projectRoot $file) -Force
}

# Spring Boot 이미지는 코드를 포함하므로 반드시 재빌드
# --no-deps: 기존 PostgreSQL을 재시작하지 않음
# --build: 새 Java 코드를 컴파일하고 테스트한 뒤 이미지를 재생성
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml up -d --no-deps --build api

# API 서비스 시작 로그 및 오류 확인
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml logs --tail=70 api

# 프론트엔드는 ./frontend:/app 바인드 마운트이므로 소스 변경이 자동 반영됨
# 실행 중인 web 컨테이너 상태 확인
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml ps
```

## API 테스트

```powershell
# 저장된 릴리스 ID를 API에서 1건 조회
$records = Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/records?size=1'
$recordId = $records.items[0].id

# 신규 버전 이력 API 확인
Invoke-RestMethod -Uri "http://localhost:8081/api/v1/records/$recordId/history" | ConvertTo-Json -Depth 6

# 잘못된 ID를 조회했을 때 HTTP 404인지 확인 (명령은 오류를 표시할 수 있음)
try {
  Invoke-WebRequest -Uri 'http://localhost:8081/api/v1/records/999999999/history'
} catch {
  Write-Host "예상한 조회 오류: $($_.Exception.Message)"
}
```

## SQL 실행계획 확인

```powershell
# :recordId로 필터링하는 쿼리의 실행계획 확인
# EXPLAIN ANALYZE는 읽기 SELECT만 실행하므로 원본 데이터를 수정하지 않음
# $recordId는 위에서 API로 조회한 숫자 ID
docker compose exec postgres psql -U signalbridge -d signalbridge -c "EXPLAIN (ANALYZE, BUFFERS) SELECT version, change_type, detected_at FROM record_versions WHERE record_id = $recordId ORDER BY version DESC LIMIT 20;"
```

## 확인사항

- 기존 페이지 이동 Fade Up 450ms와 Stagger 그대로 유지
- 릴리스 목록 / 변경 이력에서 하나 선택해 상세 패널 열기
- `변경 이력` 섹션에 `버전 1 · 최초 수집` 표시(해당 데이터가 실제 존재하는 경우)
- 재수집에서 변경이 없다면 버전 1만 나타나는 것이 정상
- 잘못된 ID 입력은 400/404 처리
- Windows 호스트 API 포트는 이전과 같이 **8081** 유지, 내부 Docker 포트는 **8080** 유지
