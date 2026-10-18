# Test plan — Phase 10 pending registration

1. Back up `signalbridge` and apply `011_pending_signups.sql` before rebuilding API.
2. Rebuild API (`docker compose ... up -d --no-deps --build api`); verify Maven unit tests pass.
3. Register a fresh email equal to the verified recipient of your Resend account while using `onboarding@resend.dev`:
   - API returns HTTP 202.
   - `pending_signups` contains the email; `app_users` must not contain that email yet.
4. Open latest verification email; confirm link in browser:
   - `pending_signups` row disappears; `app_users` has one row with `email_verified=true`.
   - Login succeeds, watchlist operations work.
5. Attempt registration with a fresh Resend-disallowed recipient (not a real person's email):
   - API returns a delivery failure (in this sandbox configuration).
   - Neither pending nor confirmed user row remains.
   - NOTE: With a verified sender domain a syntactically valid but nonexistent email might be accepted by the provider. It can remain pending until expiry, never becoming a verified account.
6. Check an existing Phase 10 unverified user from before this patch: resend verification and complete it; records and watch rules remain intact.
7. Check malformed verification tokens yield 400; verify the same valid link cannot be used twice.
8. Keep production work pending until: delivery/bounce handling, outbox and idempotent mail worker, IP-based throttling, signup abuse controls, retention cleanup, HTTPS and full integration tests.

You can inspect with DBeaver:

```sql
SELECT email,nickname,created_at,expires_at FROM pending_signups ORDER BY created_at DESC LIMIT 20;
SELECT id,email,nickname,email_verified,created_at FROM app_users ORDER BY id DESC LIMIT 20;
```
