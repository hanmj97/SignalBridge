# SignalBridge 밝은 미니멀형 디자인 적용 가이드

이 묶음은 기존 SignalBridge Phase 3 프론트엔드에서 **디자인(UI)만 교체**하는 파일입니다.

## 포함 파일
- `frontend/src/App.tsx`
- `frontend/src/styles.css`

## 적용 방법
기존 `D:\Projects\SignalBridge\frontend\src` 경로의 같은 파일을 덮어쓰면 됩니다.

### PowerShell 예시
```powershell
# 프로젝트 폴더로 이동
Set-Location 'D:\Projects\SignalBridge'

# 기존 파일 백업 폴더 생성
New-Item -ItemType Directory -Force -Path '.\frontend\src\backup-before-light-refresh'
Copy-Item .\frontend\src\App.tsx .\frontend\src\backup-before-light-refresh\App.tsx.bak
Copy-Item .\frontend\src\styles.css .\frontend\src\backup-before-light-refresh\styles.css.bak

# 다운로드한 ZIP 압축 해제
Expand-Archive -Path "$HOME\Downloads\SignalBridge-Light-Refresh.zip" -DestinationPath "$HOME\Downloads\SignalBridge-Light-Refresh" -Force

# 새 디자인 파일 복사
Copy-Item "$HOME\Downloads\SignalBridge-Light-Refresh\frontend\src\App.tsx" .\frontend\src\App.tsx -Force
Copy-Item "$HOME\Downloads\SignalBridge-Light-Refresh\frontend\src\styles.css" .\frontend\src\styles.css -Force

# 프론트엔드 컨테이너 재시작
# (기존 docker-compose.phase3.yml 기준)
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml up -d --build web

# 로그 확인
docker compose -f docker-compose.yml -f docker-compose.phase2.yml -f docker-compose.phase3.yml logs --tail=50 web
```

## 반영되는 주요 변경점
- 밝은 미니멀형 전체 테마
- 좌측 사이드바 + 상단 검색 박스
- 메인 대시보드 카드 4종
- 최근 7일 변경 추이 차트
- 최근 변경 항목 / 데이터 소스 현황 / 시스템 상태 패널
- 릴리스 목록 / 변경 이력 탭 유지
- 상세 정보 우측 슬라이드 패널 유지

## 유지되는 기능
- 기존 Spring Boot API 호출 구조
- 검색, 소스 필터, 페이징
- 상세 조회 드로어
- 모바일 반응형 동작

