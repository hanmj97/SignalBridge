# SignalBridge V2-2.1 — Opportunity detail drawer position fix

## 변경 내용
- `frontend/src/OpportunitiesPanel.tsx`
  - 상세 패널 렌더링을 `createPortal(..., document.body)`로 변경
  - transformed ancestor 또는 layout container 영향으로 상세 패널이 가운데 영역에서 열리던 문제 수정

## 적용 방법
1. 이 압축 파일을 `D:\Projects\SignalBridge`에 덮어씁니다.
2. 프런트엔드 재빌드:
   - `docker compose @compose exec web npm run build`
3. 브라우저 강력 새로고침(Ctrl+F5) 후 확인합니다.

## 기대 결과
- 청년정책·채용 탭의 상세 패널이 Engineering 릴리스 탭과 동일하게 **브라우저 우측 끝에서 슬라이드되는 형태**로 열립니다.
