# SignalBridge V2-6A — 민간 채용 API 도입 준비 (사람인)

## 목적
- 공개 사용자 서비스는 **2030 청년 지원정책**과 **채용공고** 두 종류로 한정.
- 사람인, 잡코리아, 원티드, 자소설닷컴은 공식 API/정식 제휴를 통해서만 연동.
- 기존 온통청년 / 용인시 / GitHub 및 사용자 계정, Discord, Airflow, DB의 데이터를 변경하지 않음.
- 이번 패치는 신규 파일 추가만 수행, API 호출은 명시적 opt-in, DB 쓰기 없음.

## 구성
- `collector/providers/saramin_jobs.py` — 공식 JSON 구조 파서, 공통 Opportunity 정규화, 안전한 조회 클라이언트
- `collector/saramin_v26.py` — 오프라인/라이브 조회 프리뷰 CLI
- `collector/fixtures/saramin_synthetic_v26.json` — **실제 공고가 아닌 가상 공고 2건**
- `collector/tests/test_saramin_v26.py` — 오프라인 검증 15개
- `scripts/check-v2-6a.ps1` — 검증 명령어

## 적용 (Windows PowerShell)
```powershell
# 기존 SignalBridge 개발 프로젝트 유지
Set-Location 'D:\Projects\SignalBridge'

# V2-6A ZIP은 기존 코드 덮어쓰기 없는 신규 파일만 포함
Expand-Archive -Path "$HOME\Downloads\SignalBridge-V2-6A-Saramin-ReadOnly-Adapter.zip" -DestinationPath 'D:\Projects\SignalBridge' -Force

# 네트워크/DB 접근 없는 검증
.\scripts\check-v2-6a.ps1
```

## 사람인 API 승인 신청
- https://oapi.saramin.co.kr/join 에서 이용신청
- 승인 후 앱 등록하여 `access-key` 발급
- 사전 이용 조건: https://oapi.saramin.co.kr/caution
- 공식 응답 명세: https://oapi.saramin.co.kr/guide/job-search
- `1일 500회 호출`(안내 페이지)과 `최대 500건`(주의사항)이 다르게 표기되므로 실제 배포 전 제공처 확인 필요
- API 서비스 재판매/유료 제공 금지. 소스 변형·제휴 오인 및 키 공개 금지 등 준수
- 승인받은 정보 제공·저장·재게시 범위를 확인하기 전까지 DB 적재/공개 전송 비활성화

## 승인 이후에만 진행할 읽기 전용 호출
`.env` 파일에 아래 값을 직접 추가하고 Git에 커밋하지 않습니다.
```dotenv
SARAMIN_ACCESS_KEY=YOUR_APPROVED_ACCESS_KEY
```

```powershell
# 승인된 키가 준비된 경우에만 읽기 전용 API 프리뷰
Set-Location 'D:\Projects\SignalBridge'
py -3 -m collector.saramin_v26 --live --permit-network --keywords 개발자 --count 10
```

실행 결과에는 공고 ID/기업/제목만 출력하며, **인증키·응답 JSON·원문 URL 파라미터는 출력하지 않음**. 1회 최대 20건으로 제한. PostgreSQL/Discord/Airflow에는 아무것도 기록하지 않음.

## 완료 기준
1. 단위 테스트 15개 통과
2. 가상 공고 2건 프리뷰
3. 온통청년 승인 여부와 무관하게 실행 가능
4. 사람인 공식 이용신청 완료 (API 키는 공유하지 않음)
5. 승인 후, 적법한 범위 내 라이브 프리뷰 및 데이터 이용 조건 재검토

## 주의
- 실제 공고 URL은 API 응답의 `rec_idx`를 확인 후 `https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=...` 형태로 정규화. 임의 공고 URL을 만들어내지 않음.
- 나이·소득·학력 요건은 제공된 필드만 사용. `2030 대상` 여부를 추론하지 않음.
- 마감 시각을 알 수 없는 상시 채용 공고는 `application_deadline = null`로 유지.
- 과거 소프트웨어 릴리스 및 일반 기업지원사업은 청년정책·채용 플랫폼의 메인 제공 콘텐츠에 추가하지 않음.
