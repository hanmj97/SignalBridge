# SignalBridge Phase 5 — SQL 검색 성능 진단 실습

> 기존 SignalBridge 앱은 건드리지 않습니다. PostgreSQL **별도 데이터베이스 `signalbridge_perf`** 에서만 실습합니다.

## 학습 목표

- Spring Boot `CatalogRepository.findRecords(...)` 에서 사용하는 `JOIN`, `ILIKE`, `LIMIT/OFFSET`, `ORDER BY` 의미를 이해합니다.
- 데이터 60건일 때와 대량일 때 실행계획이 어떻게 달라지는지 알아봅니다.
- `pg_trgm` + GIN 인덱스로 제목 부분문자열 검색을 가속할 수 있는지 측정합니다.
- 실 서비스와 유사한 `(r.title ILIKE ... OR s.label ILIKE ...)` 조건은 인덱스를 추가해도 빨라지지 않을 수 있음을 확인합니다.
- `UNION`을 이용해 동일 검색 의미를 유지하며 다른 계획을 유도해 봅니다.

## 절차 (Windows PowerShell)

1. Docker Desktop과 `signalbridge-postgres` 컨테이너가 실행 중인지 확인하세요.
2. 이 압축 파일의 `perf-lab` 폴더를 `D:\Projects\SignalBridge\perf-lab` 에 복사하세요.
3. PowerShell에서 아래 명령을 실행합니다.

```powershell
# SignalBridge 프로젝트 루트로 이동
Set-Location 'D:\Projects\SignalBridge'

# 테스트 DB에 샘플 150,000건 생성 → 인덱스 전/후 EXPLAIN 비교
# 실제 signalbridge DB에는 접근하지 않습니다.
.\perf-lab\run-lab.ps1

# 결과 보고서 파일 확인
Get-ChildItem '.\perf-lab\reports'
```

스크립트 실행 정책으로 차단되면 아래처럼 **프로세스 범위에서만** 실행할 수 있습니다.

```powershell
# 해당 PowerShell 프로세스에서만 스크립트 실행 허용
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# 다시 벤치마크 실행
.\perf-lab\run-lab.ps1
```

## 결과 해석

`reports/02_baseline.txt` 와 `reports/04_after.txt` 를 비교해 보세요.

| 검사 | 기대 관찰점 |
| --- | --- |
| A. 제목만 검색 | 선택도가 높은 `%RareRollbackAlert%` 조건에서 GIN 인덱스 사용 가능 |
| B. API형 OR 조건 | 서로 다른 테이블의 OR 조건 때문에 GIN 인덱스가 활용되지 않을 수 있음 |
| C. UNION 조건 | 제목 검색과 소스 이름 검색을 나누어 인덱스를 활용할 여지가 생김 |

**인덱스가 생겼다고 항상 성능이 좋아지는 것은 아닙니다.** 실제 실행계획은 서버 환경, 캐시, 비용 추정 등에 따라 달라집니다. `Execution Time`, `Buffers: shared hit/read`, `Seq Scan`, `Bitmap Index Scan`, `Bitmap Heap Scan`, `Sort` 를 근거로 판단하세요.

### 주의사항

- 벤치마크 수치는 테스트 환경의 참고 지표입니다. 실제 사용자 데이터 성능을 보장하지 않습니다.
- `EXPLAIN (ANALYZE, BUFFERS)` 는 SELECT를 실제 실행합니다. 이 실습에서는 읽기 쿼리만 사용합니다.
- 한 번 실행 시 합성 데이터 150,000건을 삽입합니다. 로컬 PC 성능에 따라 수십 초가 소요될 수 있습니다.
- `run-lab.ps1` 을 재실행하면 오직 **`signalbridge_perf` DB의 벤치마크 테이블**을 다시 채웁니다. 운영 DB는 그대로입니다.
- 벤치마크 DB 생성으로 PostgreSQL 컨테이너 디스크 사용량은 증가할 수 있습니다.
- 이번 단계는 앱 SQL을 변경하지 않습니다. 실행계획을 분석한 뒤 다음 단계에서 실제 API 튜닝 여부를 결정합니다.

## 정리 방법 (필요할 때만)

```powershell
# 성능 실험 전용 DB만 삭제합니다. 실제 signalbridge DB는 유지됩니다.
docker exec signalbridge-postgres psql -U signalbridge -d postgres -c 'DROP DATABASE IF EXISTS signalbridge_perf WITH (FORCE);'
```

## 완료 기준

1. `signalbridge_perf` DB에 150,000건 생성
2. baseline과 GIN 인덱스 적용 후의 EXPLAIN 결과 파일 확인
3. A/B/C 검색 조건에 따른 실행계획 차이 설명
4. 운영 SignalBridge 웹서비스 정상 접속 확인

완료 후 `02_baseline.txt`, `04_after.txt` 두 파일의 결과를 공유하면 실제 API에 맞는 인덱스와 SQL 개선 방향을 함께 결정할 수 있습니다.
