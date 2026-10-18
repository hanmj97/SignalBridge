# Phase 9 — local authentication + member-owned watch rules

Includes Spring Security with server-side sessions, session CSRF token, BCrypt, and per-user SQL ownership checks. All requests use the same-origin Vite `/api` proxy; no localStorage JWT. Public catalog remains readable.

**Before applying**: back up the PostgreSQL named volume or run a pg_dump. Apply 009_accounts.sql using ON_ERROR_STOP=1, then rebuild the API. Existing watch_rules with NULL owner are **preserved but disabled** and invisible to new members. They are not automatically assigned to the first account.

**Security**: The existing global Discord webhook MUST NOT receive personal watch events. The modified Airflow notifier intentionally ignores member-owned events, leaving them PENDING. Phase 10 will add per-member opt-in delivery. No production release before HTTPS, secure cookies, email verification, password recovery, login rate limiting, logging/monitoring and session-store scaling (in-memory JVM sessions are lost on restarts) are implemented.

**Requests:** GET `/api/v1/auth/csrf` returns a JSON token stored server-side in HttpSession. POST register/login/logout and POST/PATCH/DELETE watches must include `X-CSRF-TOKEN`. GET `/api/v1/auth/me` returns 401 when signed out. All `/api/v1/watches/**` require authentication; SQL queries include `user_id = :uid` for reads and mutations.

**Manual security check:** Register users A and B with different emails; A creates a rule. Sign out, sign in as B: A's rules/events should not show; B cannot patch or delete A's rule ID (404). Anonymous GET /api/v1/watches should return 401 and POST without CSRF should return 403.

**Rollback:** Restore pre-Phase9 source files, do not delete 009_accounts.sql tables/data automatically. Do not restore Phase 8 API while hosting accounts: it does not enforce user scoping!
