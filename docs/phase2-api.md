# SignalBridge Phase 2 — Spring Boot read API

> An **add-on** for the Phase 1 project at `D:\Projects\SignalBridge`.
> Do not replace `.env`, delete your named PostgreSQL volume, or run `docker compose down -v`.
> This phase does **not** change any database tables or records.

## Goals

1. Read the real GitHub release records that the Python collector already stores.
2. Expose four paginated or list-based REST endpoints.
3. Use a simple Controller -> Service -> Repository architecture.
4. Build and run Java 21 inside Docker, without a local Java/Maven installation.
5. Keep the API bound to localhost until the React UI, authentication and deployment safeguards are in place.

## Files in this add-on

```text
SignalBridge/
├── docker-compose.yml                  # EXISTING — leave as-is
├── docker-compose.phase2.yml           # NEW — defines the API service
├── .env                                # EXISTING — leave as-is, never commit
├── collector/                           # EXISTING — leave as-is
├── db/                                  # EXISTING — leave as-is
├── backend/                             # NEW — Spring Boot API
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
└── docs/phase2-api.md                   # NEW — these instructions
```

## 1. Unzip / merge

Copy the ZIP `SignalBridge-Phase2-AddOn.zip` into your Downloads folder (or change the path). **Extract its contents directly into your current project root**. The ZIP root contains `backend/`, `docs/`, and `docker-compose.phase2.yml`; avoid creating an extra nested `SignalBridge/` directory.

```powershell
# 기존 SignalBridge 루트로 이동합니다.
Set-Location 'D:\Projects\SignalBridge'

# 새 파일만 프로젝트 폴더에 압축 해제합니다. ZIP의 다운로드 위치는 실제 경로로 변경할 수 있습니다.
Expand-Archive -Path "$HOME\Downloads\SignalBridge-Phase2-AddOn.zip" -DestinationPath 'D:\Projects\SignalBridge' -Force

# 기존 파일이 그대로 있는지, 추가 파일이 보이는지 확인합니다.
Get-ChildItem .\backend
Get-Item .\docker-compose.yml, .\docker-compose.phase2.yml
```

## 2. Run

Keep Docker Desktop **Engine running**. Use the **same** project-level `.env` created in Phase 1.

```powershell
# 기존 DB와 새 API 서비스를 합친 설정이 유효한지 검증합니다.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml config

# Spring Boot 이미지를 Docker 안에서 빌드하고 서비스를 백그라운드로 실행합니다.
# --build: backend/Dockerfile을 이용해 API 이미지를 새로 빌드합니다.
# -d: 터미널을 점유하지 않고 실행합니다.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml up -d --build

# 기존 PostgreSQL과 새 API의 실행 상태를 확인합니다.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml ps

# API 기동 로그를 확인합니다. 오류가 있다면 이 출력 내용을 공유해주세요.
docker compose -f docker-compose.yml -f docker-compose.phase2.yml logs --tail=70 api
```

The Docker image build runs four unit tests as part of `mvn clean package`. The **first** build downloads Maven/Spring dependencies and may take several minutes. No DB reset is performed.

## 3. Smoke tests (PowerShell)

```powershell
# 서버 준비 상태 확인: {"status":"UP"}이 기대됩니다.
Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' | ConvertTo-Json

# 전체 GitHub 릴리스 중 최신 5개를 페이지 0부터 조회합니다.
$result = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/records?page=0&size=5'
$result | ConvertTo-Json -Depth 6

# 첫 번째 조회 결과의 실제 DB ID를 사용해 상세 조회합니다.
$id = $result.items[0].id
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/records/$id" | ConvertTo-Json -Depth 6

# 데이터 변경 이력을 조회합니다. 최초 수집 시 NEW 이력이 포함됩니다.
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/changes?page=0&size=5' | ConvertTo-Json -Depth 6

# 연결된 GitHub 출처 목록과 데이터 건수를 확인합니다.
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/sources' | ConvertTo-Json -Depth 6

# 제목이나 출처 이름에 'Docker'가 포함된 데이터만 검색합니다.
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/records?q=Docker&size=5' | ConvertTo-Json -Depth 6
```

You can open these **local** links in your browser:
- http://localhost:8080/actuator/health
- http://localhost:8080/api/v1/records?page=0&size=5
- http://localhost:8080/api/v1/changes?page=0&size=5
- http://localhost:8080/api/v1/sources

## API contracts

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/records?page=0&size=20&q=Grafana&sourceId=1` | Search and paginate latest records (q/sourceId optional) |
| GET | `/api/v1/records/{id}` | Single record plus plain-text release description |
| GET | `/api/v1/changes?page=0&size=20&sourceId=1` | List NEW/UPDATED history (sourceId optional) |
| GET | `/api/v1/sources` | List enabled source names, last checks and counts |
| GET | `/actuator/health` | Basic health probe |

Sample paginated structure:

```json
{
  "items": [{"id": 1, "sourceId": 1, "sourceLabel": "Visual Studio Code", "title": "..."}],
  "page": 0,
  "size": 5,
  "totalElements": 60,
  "totalPages": 12
}
```

The numbers are illustrative. `page` is zero-based; size must be 1–100. `q` is limited to 100 characters. Invalid pagination returns HTTP 400 and nonexistent record IDs return HTTP 404. Current sorting uses stable `(first_seen_at DESC, id DESC)` ordering. Search is case-insensitive.

## Architecture / what to learn

- `CatalogController`: HTTP route mapping and query parameter binding.
- `CatalogService`: input validation and HTTP errors (400/404).
- `CatalogRepository`: PostgreSQL `SELECT` queries via Spring JDBC, parameter binding, joins, pagination, JSONB extraction.
- `dto/`: explicit public output shapes, avoiding accidental exposure of private source ETags.
- `application.yml`: connection pooling via HikariCP (max 5 connections).
- `CorsConfig`: allow only local Vite dev UI origins; public web origins not configured.

`record_versions` stores immutable snapshots, but the `/changes` endpoint returns only metadata and changed field names. Changes are determined by the **Python collector**; this API never rewrites them.

## Troubleshooting

- **Port 8080 busy**: edit the left side of `127.0.0.1:8080:8080` to `127.0.0.1:8081:8080`, then use http://localhost:8081.
- **Image build errors**: inspect Docker Desktop network access and Maven output in `docker compose ... up -d --build`; Maven image and Maven Central require internet on first build.
- **API container won't start**: `docker compose -f docker-compose.yml -f docker-compose.phase2.yml logs --tail=100 api`.
- **Postgres healthy but 0 records**: verify the Phase 1 dataset via `docker compose exec postgres psql -U signalbridge -d signalbridge -c 'SELECT COUNT(*) FROM records;'`.
- **DB password changed**: use the same `.env` as Phase 1. Editing `POSTGRES_PASSWORD` after initializing the volume does not change the existing PostgreSQL login password.
- **Stop**: `docker compose -f docker-compose.yml -f docker-compose.phase2.yml down` preserves the volume; **do not** append `-v`.

## Not public deployment-ready yet

This is a **read-only HTTP API**, not a public production service. It has no API rate limits, end-user authentication, HTTPS reverse proxy, or dedicated least-privilege database account. Keep the API on `127.0.0.1` until those layers and abuse protections exist. Never publish `.env` or database credentials.

## Next phase

React + TypeScript UI with source filters, search, release detail, change history and source attribution. After that, add a useful official public-sector data source and user subscriptions.
