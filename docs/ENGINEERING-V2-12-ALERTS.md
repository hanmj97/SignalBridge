# Engineering V2-12 — 개인 GitHub 릴리스 Discord 알림

## 목표
공개 GitHub 저장소를 내 계정에 연결한 뒤, **기존에 연결한 Discord 채널**로
나에게 해당하는 미래 신규 릴리스 / 릴리스 내용 변경만 알림을 받을 수 있다.

이 변경은 Engineering 전용이다. 청년정책·채용 알림의 Airflow DAG를 수정하지 않는다.

## 동작 과정
1. 로그인 → **Engineering → 내 GitHub 저장소**에 공개 저장소를 등록한다.
2. **릴리스 관심 알림**에서 내 Discord 웹훅을 연결한다. 기존 연결이 있다면 재입력하지 않는다.
3. 데이터 소스에서 등록한 저장소를 선택하고:
   - 키워드를 입력해 **규칙 추가**, 또는
   - **선택 저장소 모든 릴리스 알림**을 눌러 이후 버전 전체에 대해 구독한다.
4. 이 등록 시점 **이후**의 GitHub 릴리스 신규 등록 / 내용 변경만 대상으로 한다.
5. 주기 수집 DAG `signalbridge_collector_30m`가 기존대로 알림을 매칭·발송한다.
6. 버튼 최신화 DAG `signalbridge_github_manual_refresh`도 실제 수집 성공 후
   `notify_member_watch_events` 태스크에서 알림을 매칭·발송한다.
7. `watch_events(rule_id, record_version_id)`의 UNIQUE 및 `ON CONFLICT DO NOTHING`
   때문에 같은 변경 이벤트를 수동/정기 DAG 양쪽에서 발견해도 알림 후보는 1개다.

## 개인정보와 안전성
- 회원별 `user_repository_subscriptions`에 **현재 등록되어 있는 소스**만 매칭한다.
- DB 알림 매칭 시 구독·규칙 생성 시각 이후의 버전만 처리한다.
- 실제 Discord 전송 직전에 구독이 계속 유효한지 재검사한다.
- 연결된 다른 회원의 웹훅을 사용하지 않는다. 웹훅은 DB에 암호화되어 있고
  브라우저에는 원문이 다시 표시되지 않는다.
- 기존 웹훅 연결을 테스트하기 위해 '채널 연결 · 테스트 전송'을 활용할 수 있다.
- 이전 릴리스를 삭제하거나 DB 버전을 인위적으로 수정해서 테스트하지 않는다.
- Discord 전송은 **at least once**이다. Discord 수신 직후 프로세스가 중단되면
  매우 드물게 재시도로 중복 알림이 전송될 수 있다.

## 적용 순서 (PowerShell)
- 로컬 Git의 미커밋 변경사항을 먼저 확인한다.
- `git pull --ff-only origin feature/youth-policy-platform`
- `docker compose ... up -d --no-deps --build api` (인증된 소스 제한 로직 변경)
- `docker compose ... exec web npm run build`
- `docker exec signalbridge-airflow airflow dags list-import-errors`
- `docker exec signalbridge-airflow airflow tasks list signalbridge_github_manual_refresh`
- `docker exec signalbridge-airflow airflow dags unpause signalbridge_github_manual_refresh`
- 기존 30분 DAG도 활성 상태로 유지한다.
- DB 마이그레이션은 필요 없다.

## 확인 SQL
다음 SQL은 반드시 `signalbridge` DB에서 실행한다.

```sql
-- 회원별 구독 현황 (웹훅/비밀번호를 출력하지 않음)
SELECT u.user_id, s.resource, u.created_at
FROM user_repository_subscriptions u
JOIN sources s ON s.id=u.source_id
ORDER BY u.user_id, s.resource;

-- 규칙/이벤트 집계 (회원별 분리, 민감 URL 미출력)
SELECT w.user_id, s.resource, w.keyword, w.enabled,
       COUNT(e.id) AS event_count,
       COUNT(e.id) FILTER (WHERE e.status='SENT') AS sent_count,
       COUNT(e.id) FILTER (WHERE e.status='PENDING') AS pending_count
FROM watch_rules w
LEFT JOIN sources s ON s.id=w.source_id
LEFT JOIN watch_events e ON e.rule_id=w.id
WHERE w.user_id IS NOT NULL
GROUP BY w.id,w.user_id,s.resource,w.keyword,w.enabled
ORDER BY w.user_id,w.id DESC;
```

새 릴리스가 실제로 게시되기 전에는 `event_count = 0`이어도 정상이다.
알림 발송이 없으면 Airflow의 `notify_member_watch_events` 태스크,
Discord 연결 및 `SIGNALBRIDGE_DISCORD_ENCRYPTION_KEY` 설정을 확인한다.
API 키·웹훅·암호화 키를 대화나 GitHub 이슈에 붙여넣지 않는다.

## 검증 범위
GitHub Actions: Python 수집/알림 테스트, Maven/JUnit, TypeScript 빌드, 비밀정보 검사.
실제 회원의 Discord 메시지 수신은 로컬 통합 테스트로 별도 확인한다.
