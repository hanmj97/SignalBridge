# SignalBridge 운영·배포 전 점검표 (Phase 15A)

**범위:** 로컬 Docker/학습 단계에서 공개 서비스로 넘어가기 전 반드시 해결할 위험을 정리한 문서. Phase 15A 패치만으로 production ready가 되지 않습니다.

## 현재 확인된 아키텍처

- PostgreSQL 17, 데이터 컬렉터(Python + GitHub Releases API), Apache Airflow 2.11 standalone
- Spring Boot 3 / Java 21 / Spring JDBC / Spring Security + 세션
- React / TypeScript / Vite
- Resend API 이메일 인증·비밀번호 재설정, 회원별 Discord 웹훅 암호화 저장·알림
- `127.0.0.1`에 바인딩된 포트: PostgreSQL 5434, API 8080(실제 접근 시 8081일 수 있음), 웹 5173, Airflow 8082. 각 환경의 실제 매핑은 `docker compose ps`로 확인.

## 배포 차단 항목 (P0)

- [ ] **비밀키 회전:** 이전에 화면에서 노출된 Resend API 키 재발급; Discord 암호화 키도 채널 재연결을 계획한 후 안전하게 교체. 민감정보가 한 번이라도 Git 기록에 들어갔다면 ignore만으로 해결되지 않음.
- [ ] **TLS:** 공개 도메인 + HTTPS, HSTS, secure/HttpOnly/SameSite 쿠키, reverse proxy, 통신 경로 점검.
- [ ] **회원 세션 철회:** 비밀번호 변경·재설정, 이메일 변경, 탈퇴 후 다른 브라우저에 남은 인증 세션까지 무효화. 현재 세션 저장소는 컨테이너 메모리 기반.
- [ ] **요청 제한:** IP·계정 기준 로그인, 회원가입, 메일 재발송, 비밀번호 재설정, 웹훅 연결 테스트 제한. 외부 API를 악용한 비용 발생도 제한.
- [ ] **발신 도메인 검증:** 현재 `onboarding@resend.dev` 테스트 방식은 일반 사용자 대상 이메일 발송이 제한됨.
- [ ] **Airflow 보호:** standalone + SQLite 및 관리자 UI를 인터넷에 노출하지 말 것. 프로덕션 메타데이터 DB와 인증·로그 보안 설계.
- [ ] **데이터 보존:** 계정 탈퇴 시 앱 DB에서 삭제해도 과거 백업·Discord 전송 메시지는 남을 수 있음. 개인정보 보존·삭제 정책 문서화.
- [ ] **통합 테스트:** 두 계정/두 브라우저로 계정 소유권 분리, CSRF, 세션 무효화, 재사용 토큰 차단 및 API 실패 응답 검증.

## 다음 수준 (P1)

- [ ] DB migrations를 Flyway/Liquibase처럼 버전 관리 및 재적용 검증 가능한 도구로 관리.
- [ ] Resend 전송과 가입 상태 변경을 비동기 outbox로 분리하고 재시도·중복 전송·데드레터 처리.
- [ ] Webhook 전송 실패 로그는 URL/토큰/이메일 본문을 노출하지 않도록 구성.
- [ ] Docker 이미지 digest 및 Python/Node/Maven dependency lockfile 관리; 주기적 취약점 스캔.
- [ ] 관측성: p95 API 응답 시간, DAG success/failure, 수집 최신성, DB 용량, 큐 적체, 알림 실패율.
- [ ] Docker 컨테이너 자원 제한, PostgreSQL 접속/백업 계정 최소 권한, 배포·롤백 런북.

## 로컬 운영 확인

```powershell
# 실제 컨테이너 상태/포트 매핑 확인 (민감정보 출력하지 않음)
docker compose @compose ps

# 비밀값을 숨기는 읽기 전용 preflight
py -3 .\tools\phase15_preflight.py

# 로컬 Python 테스트 및 CI 검사
.\scripts\check-phase15.ps1
```

## 안전한 백업

```powershell
# 운영 DB에는 쓰지 않고 custom-format 백업만 수행
.\scripts\phase15-backup.ps1
```

백업 파일은 기본적으로 `$HOME\SignalBridge-PrivateBackups`에 저장합니다. 이 폴더는 Git 프로젝트 밖에 있으므로 잘못 업로드할 위험이 줄어듭니다. 그래도 **실제 개인 데이터가 포함**되어 있습니다.

## 복원 훈련 지침 (별도 테스트 DB에만; 직접 승인하고 수행)

복원 무결성은 archive 목록 검사만으로 완전히 검증되지 않습니다. **실제 운영 DB에 복원하지 말고**, 테스트용 PostgreSQL 인스턴스 또는 새로 만든 데이터베이스에 격리해 시험해야 합니다.

1. 백업 파일을 보호된 장소에 보관하고 검증용 복제본으로 작업.
2. 운영 DB `signalbridge`와 이름이 다른 임시 DB(예: `signalbridge_restore_drill`)를 별도로 생성. 같은 볼륨/인스턴스여도 복원 대상명을 다시 확인.
3. `pg_restore --no-owner --no-privileges --dbname=signalbridge_restore_drill`로 복원. 운영 DB 이름은 대상에 절대 사용하지 않음.
4. 테이블 수, 레코드 수 및 FK 무결성 검사. 회원 데이터가 포함되므로 테스트 DB에도 접근 제한.
5. 확인 후 격리 DB 제거. 본 데이터베이스와 Docker 볼륨은 절대 `down -v` 하지 않음.

위 절차를 **자동 실행하지 않는 이유**는 삭제/복원 대상 오입력으로 인한 사고 방지입니다.

## CI 파이프라인

`.github/workflows/signalbridge-ci.yml`:
- Git tracked 파일에서 실제 비밀키 의심 문자열 및 민감 파일 확인(일치 문자열 자체는 출력하지 않음)
- Python preflight 테스트 / 기존 Airflow + collector unittest
- Java 21 Maven unit tests
- Node 22 TypeScript 및 Vite build

**한계:** GitHub CI는 실제 운영 DB, Airflow scheduler, Resend, Discord에 연결하지 않습니다. 4가지 작업 성공은 정적/단위 수준의 통과이며, 공개 배포 승인과 다릅니다.

## 기술 포트폴리오로 정리할 핵심

- Docker 기반 운영 점검, 자동 테스트, CI, 민감정보 관리
- PostgreSQL custom-format 백업 검증 및 복구 계획
- Airflow 주기 수집/멱등성, 관심 이벤트 생성 및 회원별 알림 격리
- Spring Security + CSRF + 회원 인증 구조와, 공개 운영 시 남은 과제를 숨기지 않는 위협 모델
- **성능 개선 결과(테스트 데이터 15만 건)**: 희소 검색 OR 중앙값 234.813ms → UNION 7.790ms, 흔한 검색에서는 OR 44.919ms < UNION 242.807ms. 인덱스가 항상 빠르지는 않다는 결론을 정직하게 제시.
