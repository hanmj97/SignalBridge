# SignalBridge v0.2.0 — Portfolio MVP (릴리스 설명 초안)

> **Pre-release | 로컬 포트폴리오 MVP**  
> 본 릴리스는 외부 사용자가 접속하는 정식 배포가 아닙니다. 2030 청년정책·채용공고 및 Engineering 영역을 나눠 개발한 로컬 버전의 구현·검증 이력을 기록합니다.

## 주요 기능

- React + Spring Boot + PostgreSQL 기반 정책·채용정보 검색 및 GitHub Releases 모니터링
- 용인시 공식 API `채용정보` 유형만 실데이터 수집, LAB DB 적재, 중복 방지 및 변경 이력 관리
- Apache Airflow 수동·정기 수집 워크플로 구성(로컬 환경)
- 회원가입·로그인·이메일 인증, 회원별 관심 규칙 및 Discord 알림 구조
- Engineering: 등록한 공개 GitHub 저장소의 실제 릴리스 감지 및 Discord 알림 검증
- Python/JUnit/TypeScript 테스트와 GitHub Actions CI, 보안 사전점검 도구
- PostgreSQL custom-format 백업·복원 훈련 안내 및 DB 성능 실험 결과 정리

## 검증된 범위

- 용인시 공식 API 조회 → `signalbridge_v2_lab` 적재 → 재수집 시 중복 방지 → Spring Boot/React 조회 (개발자 로컬 확인)
- Airflow 용인시 DAG 수동 실행 및 정기 스케줄 활성화 (개발자 로컬 확인)
- GitHub 테스트 릴리스 등록 → 수집·변경 감지 → Discord 알림 수신 (개발자 로컬 확인)
- GitHub Actions 정적·단위 테스트 및 빌드 통과 이력
- 백업/복원: 방법과 도구 준비. **실제 격리 복원 성공 증적은 별도 확인 전이므로 완료로 주장하지 않음**

## 알려진 제한 사항

- 온통청년, 사람인, 잡코리아 공식 API는 이용 승인/키 발급 대기. 원티드·자소설닷컴은 공식 연동 가능 범위 미확인.
- 용인시 응답에는 개별 공고 지원 URL이 보장되지 않아 `DATASET` 링크만 저장; 이 데이터는 개인별 Discord 전송 대상에서 제외.
- 청년 지원 자격, 실제 채용 근무 지역, 마감 상태는 출처 근거 없이 추정하지 않음.
- 로컬 Docker가 중지되면 Airflow 일간 수집도 중단. 제공처 페이징 한도 내 수집이므로 전체 공고 보장 불가.
- HTTPS, 공개 서비스 보안, 개인정보 보호, 복구 훈련 등의 작업 완료 전에는 외부 공개 배포에 사용하지 말 것.

## 관련 문서

- [README](../README.md)
- [시스템 아키텍처](architecture.md)
- [운영 및 백업](PHASE15-OPERATIONS.md)
- [용인시 실데이터 연동](V2-7B-YONGIN-JOBS-ONLY.md)

**배포 유형:** Pre-release로 발행 권장. 최종 문서 커밋이 기본 브랜치에 반영되고 해당 커밋의 CI를 확인한 다음 태그를 생성합니다.
