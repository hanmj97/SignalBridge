# Phase 13 — 비밀번호 재설정 설계 / 검증 관점

**데이터 흐름:** 브라우저 요청 → Spring Security CSRF → Controller (항상 202) → Service 계정 조회 → 랜덤 토큰 생성 → 토큰 해시 DB 보관 → Resend 이메일 → 링크 → 새 비밀번호 입력 → SQL로 단일 사용 토큰 DELETE RETURNING → BCrypt 업데이트 → 204 → 로그인.

- `password_reset_tokens.user_id` PK: 회원별 미소비 토큰 한 건만 유지
- `token_hash` UNIQUE: 평문 토큰 저장하지 않음
- `expires_at` / `issued_at`: 만료/재발송 제한
- `@Transactional`: 검증/소비와 비밀번호 UPDATE를 하나의 DB 트랜잭션으로 보장
- 계정 존재/발송 성공 여부 비공개: `202` 고정
- 위협 모델: 계정 열거, 토큰 재사용, 타 회원 비밀번호 덮어쓰기, 인증되지 않은 가입, 웹훅/비밀키 노출을 방지

**장기 운영에서 보완할 점:** 세션 일괄 무효화 및 비밀번호 변경 알림(새 메일), IP별 속도 제한/봇 차단, 외부 메일 전송 outbox, 마이그레이션 자동화 Flyway/Liquibase, 서버 측 감사 기록, 테스트로 429/503 처리 검증, TLS & Secure Cookies, 인증 이메일 발송 도메인 연결.
