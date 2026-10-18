# SignalBridge V2-4 | 청년정책 API 사전검증 및 오프라인 파이프라인

## 목표
- 온통청년 API 인증키 승인 전, 네트워크 호출 없이 XML 파싱·정규화·변경 감지 자동 테스트
- V2-1 `save_opportunity`, `write_fixture()` 재사용: 실습 DB `signalbridge_v2_lab`만 쓰고 `is_demo=true` 보장
- 선택적 실제 API 요청은 인증키 승인 후 명시적 플래그가 있을 때만 수행하고 **응답의 최상위 XML 태그만 출력**; 정책 데이터 저장/매핑은 수행하지 않음
- 기존 `signalbridge` 회원 DB, Discord 알림, 기존 Airflow DAG와 UI는 변경하지 않음

## 주의
- 이 XML 형식(`SignalBridgeSyntheticFeed`)은 **SignalBridge 테스트용으로 직접 정의한 가상 형식**이다. 온통청년의 실제 XML 구조가 아니다.
- 공개 공식 문서의 요청 예시 `https://www.youthcenter.go.kr/opi/youthPlcyList.do` 및 `openApiVlak`, `pageIndex`, `display`는 참고용이다. **실제 서비스 제공 URL·파라미터·응답 스키마는 승인 후 공식 문서를 기반으로 재확인**해야 한다.
- API 키, XML 본문, `.env`, Discord 웹훅 URL을 채팅/스크린샷/GitHub에 보내지 않는다.

## 적용
```powershell
# 프로젝트 작업 브랜치와 변경 여부 확인
Set-Location 'D:\Projects\SignalBridge'
git status --short

# 다운로드 ZIP의 경로를 확인한 후 기존 프로젝트에 압축 해제
Expand-Archive -Path "$HOME\Downloads\SignalBridge-V2-4-Youthcenter-Preflight.zip" -DestinationPath 'D:\Projects\SignalBridge' -Force

# 오프라인 검사: 네트워크, DB 조작 없음
.\scripts\check-v2-4.ps1
```

## 실습 DB 데모 파이프라인 (선택적 / 직접 실행)
V2-1을 마쳤으므로 `signalbridge_v2_lab` DB가 있어야 한다. 이 데모 fixture의 ID는 V2-1과 달라 초기 2건이 추가된다.
```powershell
# 1) 새로운 가상 XML 2건 저장
py -3 -m collector.youthcenter_v24 --fixture '.\collector\fixtures\youthcenter_synthetic_v24.xml' --write-demo
# 예상: new=2 updated=0 unchanged=0 (처음 실행할 때)

# 2) 동일 fixture 재수집 -> 중복/불필요한 새 버전 방지
py -3 -m collector.youthcenter_v24 --fixture '.\collector\fixtures\youthcenter_synthetic_v24.xml' --write-demo
# 예상: new=0 updated=0 unchanged=2

# 3) 가상 정책의 신청 마감일 변경 -> 변경 버전 1건 생성
py -3 -m collector.youthcenter_v24 --fixture '.\collector\fixtures\youthcenter_synthetic_v24_updated.xml' --write-demo
# 예상: new=0 updated=1 unchanged=1
```
기존 데모 공고 3건 + 신규 2건이 V2 화면에 표시될 수 있다. 모두 `is_demo=true`이며 실제 알림 전송 대상에서 제외된다. 이미 실행했다면 카운트는 달라질 수 있다. 실습 중 정규화가 바뀐 뒤 원본 fixture를 다시 실행하면 재변경으로 감지될 수 있다.

## DBeaver 읽기 전용 검증
`signalbridge_v2_lab` DB에서 실행:
```sql
SELECT r.external_id, r.title, r.current_version, d.kind,
       d.application_deadline, d.is_demo
FROM records r
JOIN opportunity_details d ON d.record_id = r.id
WHERE r.external_id LIKE 'SB-V24-FAKE-%'
ORDER BY r.external_id;
```

## Airflow DAG 확인
새 DAG는 `signalbridge_youthcenter_offline_preflight`이고 `schedule=None`, `is_paused_upon_creation=True`다. `airflow/dags`, `collector`가 기존처럼 볼륨 마운트되면 Airflow가 새 DAG를 발견한다.
```powershell
# 기존 $compose 정의를 사용 (V2-3까지 설정 포함)
docker compose @compose exec airflow airflow dags list-import-errors
docker compose @compose exec airflow airflow tasks list signalbridge_youthcenter_offline_preflight
```
Airflow UI `http://localhost:8082`에서 **수동으로만 Trigger**하여 2개 태스크가 성공하는지 확인한다. 이 DAG는 DB에 쓰거나 실제 API를 호출하지 않으며, Discord도 발송하지 않는다.

## 승인 후 별도 수행 (현재 진행하지 않음)
API 키는 `YOUTHCENTER_API_KEY` 환경변수로만 설정하고 다음 명령을 **명시적으로 승인한 뒤** 실행:
```powershell
# 발급 이후에만: 네트워크 호출, DB 쓰기 없음
py -3 -m collector.youthcenter_v24 --inspect-live --permit-network
```
XML 최상위 태그와 태그 이름만 출력하며, 실데이터 내용과 인증키는 출력하지 않는다. 결과 태그와 최신 공식 명세를 검증한 뒤 **다음 단계에서 실데이터 → 공통 스키마 변환 및 메인 운영 DB 이관을 별도로 진행**한다.

## V2-4 미구현 항목
- 온통청년 실제 응답 필드의 최종 매핑 및 정합성 검증
- 실제 정책 자동 수집 스케줄, 실데이터 저장, 알림 발송
- 공공/민간 채용 제공처 개별 연동
모두 API 권한과 응답 구조 확인 후 후속 단계에서 활성화한다.
