# SignalBridge Phase 12 — Account settings

## Scope

This patch adds an authenticated **My Page** to the existing Phase 11 project. No DB migrations or existing tables are changed.

- `GET /api/v1/account/profile`: email, nickname, email verification, account creation time (never a password hash)
- `PATCH /api/v1/account/nickname`: validates 2–40 characters, updates only the authenticated account
- `POST /api/v1/account/password`: requires current password, enforces original 12-character minimum/72-byte maximum, rejects reuse, updates bcrypt hash, invalidates current HTTP session
- React `AccountPanel.tsx`: account facts, Discord connection status (existing Phase 11 API), forms, error feedback
- App: sidebar My Page, clickable nickname in topbar, login guard, unchanged 450ms page motion

## Security notes

1. Always derive user ID from `@AuthenticationPrincipal`, not request body or URL.
2. `AccountSecurityConfig` explicitly requires authentication for all account endpoints (including GET) *before* the public catalog GET rule. CSRF checks remain active for unsafe HTTP verbs.
3. A password change invalidates the **current browser's session**, but does **not** forcefully revoke *other existing sessions*. Deploying publicly requires server-side global session revocation/session versioning, HTTPS, request throttling, and audit logging.
4. Never include password hashes or credentials in API responses or logged requests. Verification email stays immutable in this phase.
5. No account deletion yet. Cascading deletion could affect `watch_rules`, `watch_events`, `member_discord_channels`, so deletion needs an explicit, tested retention plan.
6. Prior screenshot included secrets. Rotate exposed Resend credentials, and only rotate the Discord encryption key after planning for saved ciphertext compatibility/reconnection.

## DBeaver checks (read-only)

```sql
SELECT id, email, nickname, email_verified, created_at
FROM app_users
ORDER BY id DESC;

SELECT u.nickname, (dc.user_id IS NOT NULL AND dc.enabled) AS discord_connected
FROM app_users u
LEFT JOIN member_discord_channels dc ON dc.user_id=u.id
ORDER BY u.id;
```

## Manual acceptance steps

1. As an anonymous visitor, click **마이페이지**; the login prompt is shown. The API returns 401 without a session.
2. Sign in; **마이페이지** shows verified email, join date and Discord connection state.
3. Change nickname. Verify topbar updates and survives page refresh and re-login.
4. Enter incorrect current password; request must fail and old password must remain usable.
5. Enter correct current password with a different 12+ character new password; current browser session ends.
6. Confirm old password no longer allows login, new password does.
7. Confirm existing watch rules, Discord channel, email verification, releases, Airflow DAG still work.
