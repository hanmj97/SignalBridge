# SignalBridge Phase 5.5 — Search Strategy Experiment

## 목적

- 변경 범위: `CatalogRepository.findRecords` 조회 SQL만 (기본 OR 유지)
- 선택적 `UNION` 전략은 환경변수 `SIGNALBRIDGE_SEARCH_STRATEGY`로 활성화 가능
- 프론트엔드, SQL 스키마 및 데이터 수집기는 변경 없음
- **시범 사용 목적. 대량 검색 시 UNION이 OR보다 느려질 수 있음.**

## 구성 요소

- `backend/src/main/java/dev/signalbridge/api/repository/CatalogRepository.java` — 조회 SQL 스위치
- `backend/src/main/resources/application.yml` — 설정 기본값 `OR`
- `backend/src/test/java/dev/signalbridge/api/repository/CatalogRepositorySearchStrategyTest.java` — SQL 구조와 특수문자 테스트
- `docker-compose.search-experiment.yml` — 컨테이너에 환경변수 전달

## 설치 및 검증 (PowerShell)

ZIP을 `D:\Projects\SignalBridge`에 풀기 전에 변경 대상 2개 파일을 반드시 백업하세요.

```powershell
# 프로젝트 경로 이동
Set-Location 'D:\Projects\SignalBridge'

# 수정 대상 기존 파일 백업
New-Item -ItemType Directory -Force -Path '.\backups\search-experiment'
Copy-Item .\backend\src\main\java\dev\signalbridge\api\repository\CatalogRepository.java .\backups\search-experiment\CatalogRepository.java
Copy-Item .\backend\src\main\resources\application.yml .\backups\search-experiment\application.yml

# 다운받은 파일을 프로젝트 폴더에 압축 해제
Expand-Archive -Path "$HOME\Downloads\SignalBridge-Phase5.5-API-Search-Experiment.zip" -DestinationPath 'D:\Projects\SignalBridge' -Force

# 기본 OR 전략으로 컴파일/단위테스트 포함 빌드
$env:SIGNALBRIDGE_SEARCH_STRATEGY = 'OR'
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml -f docker-compose.search-experiment.yml up -d --no-deps --build api

# 빌드 및 기동 로그 확인
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml -f docker-compose.search-experiment.yml logs --tail=70 api

# API 응답 예시 검증
Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/records?q=Grafana&page=0&size=5' | ConvertTo-Json -Depth 5

# 필요할 때만 UNION 실험 활성화 (운영 적용 권장이 아님)
$env:SIGNALBRIDGE_SEARCH_STRATEGY = 'UNION'
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml -f docker-compose.search-experiment.yml up -d --no-deps --force-recreate api

# UNION으로도 목록, 건수, 정렬, 페이지 검증
Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/records?q=Grafana&page=0&size=5' | ConvertTo-Json -Depth 5

# 테스트 후 OR 전략 복구
$env:SIGNALBRIDGE_SEARCH_STRATEGY = 'OR'
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml -f docker-compose.search-experiment.yml up -d --no-deps --force-recreate api
```

## 참고사항

- 8개 케이스의 `signalbridge_perf` 테스트는 7개에서 UNION이 빠르고, 매우 흔한 키워드에서는 OR가 약 5.4배 빨랐습니다.
- 이번 패치는 운영 `records` DB의 인덱스를 **자동으로 만들지 않습니다**. 소량 데이터인 실제 서비스에서 시간 개선이 나타날 것이라 가정하면 안 됩니다.
- 쿼리 특성별 자동 분기(AUTO)를 추가하지 않았습니다. 검색 빈도 정보 없이 문자열 길이만 보고 속도를 예측하는 방식은 부정확합니다.
- `UNION`은 중복 제거를 수행하므로 `OR`와 동일한 ID 집합을 반환하도록 설계했습니다. 모두 `ORDER BY first_seen_at DESC, id DESC` 유지.
- CORS / API 엔드포인트 / 응답 DTO는 그대로입니다.
- 테스트 파일은 SQL 생성 동작을 검증하며, 실제 DB 결과 동등성은 API 테스트 및 기존 실습과 함께 확인하세요.
