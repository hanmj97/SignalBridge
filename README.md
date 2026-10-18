# Phase 10 — HTTP status / error dispatch fix

## Why

In `AccountSecurityConfig.java`, `.anyRequest().denyAll()` may intercept Spring Boot's internal `DispatcherType.ERROR` dispatch to `/error` when `verify-email` rejects a malformed token with HTTP 400. The 400 can be masked as 401. The Phase 10 PowerShell smoke test expects the actual status 400.

## Change

One Java file only:
- import `jakarta.servlet.DispatcherType`;
- insert `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` at the beginning of `authorizeHttpRequests`;
- keep all other authorization, CSRF, and authenticated watch rule restrictions unchanged.

This allows an **internal error dispatch**, not arbitrary anonymous access to endpoints. It does not turn off CSRF or modify any database tables.

## Apply

Back up `backend/src/main/java/dev/signalbridge/api/config/AccountSecurityConfig.java`, replace it with the file in this patch, rebuild only the API container, and rerun `scripts/check-phase10-email.ps1`. No DB migration or frontend rebuild is necessary.

If it still returns HTTP 401 (or now returns 403), inspect the Spring Boot API logs around the test request. A 403 would indicate a distinct CSRF header/session problem, whereas a 401 after this patch would suggest a stale build or a separate auth mapping failure.
