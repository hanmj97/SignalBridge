# SignalBridge V2-5A — 다중 API 제공처 기반과 용인시 채용 API 안전 조회

## 목표와 제한
- 기존 프런트엔드/백엔드/DB/Airflow/Discord 변경 없음. 새 파일만 추가.
- 공식 API의 개발계정 자동승인은 서비스 인증키가 필요 없다는 뜻이 아님.
- 현재 구현: 제공처 레지스트리 및 용인시 API read-only 검사(실제 API 키가 있는 경우).
- **실제 레코드 INSERT / UPDATE / Discord 알림은 구현하지 않음.** 용인시 API 명세에는 공고별 원문 URL이 보장되지 않으므로 임의 주소를 만들어 저장하지 않음.
- `youthcenter`, `bizinfo_support`, `saramin`, `work24`, `jobkorea`, `wanted`, `jasoseol` 등록은 향후 개발을 위한 레지스트리 항목임. 실제 API가 모두 연결된 상태가 아님.

## 1. 사전 준비
기존 프로젝트 `D:\Projects\SignalBridge`에서 `SignalBridge-V2-5A-Multi-Provider-Preflight.zip`을 압축 해제. 신규 파일만 추가.

## 2. 오프라인 검증

```powershell
# 환경변수나 실제 DB를 읽지 않는 모의 응답 테스트
.\scripts\check-v2-5.ps1
```

## 3. 가장 빨리 쓸 수 있는 공식 채용 API
- **용인시 채용 및 시험 정보 조회**: <https://www.data.go.kr/data/15097328/openapi.do>
- 개발 및 운영 자동승인으로 표시됨. 공공데이터포털 회원가입 / 활용신청 / 발급 키 확인 필요.
- 응답 공개 명세: 공고번호, 제목, 종류, 부서, 접수시작/종료일, 접수여부 등. 공고별 원문 URL 필드는 명세에 없음.
- 허가 후 직접 발급받은 키를 **로컬 `.env`**에만 입력:

```dotenv
YONGIN_API_SERVICE_KEY=your_personal_service_key
```

- 실제 호출할 때만 아래 명령을 실행. 키는 채팅에 붙여넣지 말고 공유한 압축에도 넣지 마세요.

```powershell
# 사용자의 컴퓨터에서만 명시적으로 HTTP 요청 실행
py -3 -m collector.provider_v25 --live-yongin --permit-network
```

`--live-yongin`은 공고 제목과 ID를 최대 5건 출력하고, 데이터베이스에는 쓰지 않음.
공고 데이터가 없으면 `0건`이 정상일 수 있음. 응답 형태가 예상과 다르면 추가 필드 매핑을 검증해야 함.

## 4. 지원사업 API 병행 준비
- 중소벤처기업부 중소기업 지원사업 공고 조회: <https://www.data.go.kr/data/15157820/openapi.do>
- 개발계정 자동승인 / 운영계정 심의승인. 출처표시 및 변경금지 제3유형.
- 공식 JSON/XML 샘플과 상세 명세 확인 후 `collector/providers/bizinfo_support.py` 어댑터를 연결할 계획.

## 5. 보안 규칙
- 운영 API 키와 사용자 개인정보는 GitHub, 스크린샷, 채팅에서 공유하지 않음.
- `requests` 오류 원문에는 URL 및 키가 포함될 수 있어 오류 원문 출력 차단.
- HTTP 리다이렉트 차단, HTTPS 고정 엔드포인트, 응답 크기 제한, 타임아웃 적용.
- 인증키가 없어도 모든 오프라인 단위 테스트 동작.
- 기존 회원 계정 DB `signalbridge` 및 실습 DB `signalbridge_v2_lab` 데이터는 변경하지 않음.
