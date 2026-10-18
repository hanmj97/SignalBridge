# SignalBridge Phase 14 — Email Change & Account Deletion

**Target**: Working SignalBridge after Phase 13 Password Recovery + Login Redirect Fix, Phase 12 My Page, Phase 11 member Discord, Phase 10 pending-signup hotfix.

## What changes

- Authenticated member can request an email change using their current password. No immediate `app_users` update.
- A random 32-byte one-use token is mailed **to the new address** via the existing Resend service. Only SHA-256 of the token is saved, 30-minute expiration, 2-minute resend throttle.
- Valid token confirmation changes the email **of the existing member ID**, retaining watch rules and Discord configuration; old recovery and verification tokens are revoked.
- Confirmation invalidates the session in the browser that confirms the email change. The member must sign in again with their new email.
- Member deletion requires current password, typing `DELETE`, and a second browser confirmation. The database deletes the `app_users` row, cascading watch rules/events and encrypted Discord destination. Public GitHub records/versions/sources/ingestion runs are untouched.
- React shows both operations in the existing My Page. A dedicated public email-change confirmation UI handles incoming `?changeEmail=TOKEN` links without requiring a session.
- Does not change Spring Security configuration or other authentication endpoints.

**Limitations**:
- With `onboarding@resend.dev`, Resend may restrict recipients to the address registered on the Resend account. Different-user email-change delivery cannot be end-to-end tested until a sender domain is verified.
- The current server uses in-memory HTTP sessions. Email changes and deletions invalidate the session **in the confirming/requesting browser only**; existing sessions in other browsers are not centrally revoked. Complete session revocation is required before public deployment. The deleted user's DB record is gone, but other stale sessions may persist until expiration; affected APIs should reject missing accounts.
- Email delivery is a synchronous external API call within a DB transaction (learning implementation). Consider outbox/async after hardening.
- In-flight Airflow jobs may have already queued/delivered messages around deletion. Do not promise cancellation of already-running HTTP requests.
- Local backup archives and `.env` contain sensitive data. Do not commit to GitHub.

## 1. BACK UP FIRST

Open PowerShell in `D:\Projects\SignalBridge`:

```powershell
# Target project root
Set-Location 'D:\Projects\SignalBridge'

# Time-stamped backup directory
$backup = Join-Path (Get-Location).Path ('backups\before-phase14-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null

# PostgreSQL custom-format backup
# -Fc: suitable for pg_restore, includes app_users and related user data
docker exec signalbridge-postgres pg_dump -U signalbridge -d signalbridge -Fc -f /tmp/pre_phase14.dump
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL backup failed' }
docker cp 'signalbridge-postgres:/tmp/pre_phase14.dump' (Join-Path $backup 'pre_phase14.dump')
if ($LASTEXITCODE -ne 0) { throw 'Failed to copy PostgreSQL backup' }

# Existing files modified by this patch; keep their subdirectories
$files = @(
  'backend\src\main\java\dev\signalbridge\api\auth\ResendEmailSender.java',
  'frontend\src\App.tsx',
  'frontend\src\AccountPanel.tsx',
  'frontend\src\api.ts',
  'frontend\src\styles.css'
)
foreach($file in $files) {
  $target = Join-Path $backup $file
  New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
  Copy-Item $file $target -Force
}
```

## 2. Apply migration and code

```powershell
# Unpack downloaded ZIP into a temporary staging folder
$zip = Join-Path $HOME 'Downloads\SignalBridge-Phase14-Account-Lifecycle.zip'
$staging = Join-Path $env:TEMP 'signalbridge-phase14-staging'
Expand-Archive -Path $zip -DestinationPath $staging -Force

# Copy and apply ONLY the new migration to the existing signalbridge database
docker cp (Join-Path $staging 'db\migrations\014_account_lifecycle.sql') 'signalbridge-postgres:/tmp/014_account_lifecycle.sql'
if ($LASTEXITCODE -ne 0) { throw 'Failed to copy migration' }
docker exec signalbridge-postgres psql -v ON_ERROR_STOP=1 -U signalbridge -d signalbridge -f /tmp/014_account_lifecycle.sql
if ($LASTEXITCODE -ne 0) { throw 'Phase 14 migration failed, stop here' }

# Apply patch files. This ZIP contains no .env or Docker compose files.
Expand-Archive -Path $zip -DestinationPath 'D:\Projects\SignalBridge' -Force
```

## 3. Rebuild API, check React

```powershell
# Compose files used by the existing Phase 13 stack
$compose = @(
 '-f','docker-compose.yml',
 '-f','docker-compose.phase2.yml',
 '-f','docker-compose.phase3.yml',
 '-f','docker-compose.search-experiment.yml',
 '-f','docker-compose.phase6.yml',
 '-f','docker-compose.phase10-email.yml',
 '-f','docker-compose.phase11-discord.yml'
)

# Never print the resolved Compose config because it may contain secrets
docker compose @compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose config invalid' }

# Compile and test the backend, only replace API container
docker compose @compose up -d --no-deps --build api
if ($LASTEXITCODE -ne 0) { throw 'API build failed; restore the saved Java sources' }

# Typecheck & build frontend via the existing bind mount
docker compose @compose exec web npm run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed; inspect output' }

# Tests that do not send email or delete accounts
.\scripts\check-phase14.ps1
```

Refresh `http://localhost:5173` with Ctrl+Shift+R; check **마이페이지**.

## 4. Safe manual checks

- **Email change**: sign in, open My Page, enter **new email + current password**. The server should return 202 and send a confirmation link to the *new* email. Until the link is confirmed, DB `app_users.email` must remain unchanged. Confirm once → original `app_users.id`, watch rules and Discord record remain. Same link twice → HTTP 400. Confirming browser is signed out; log in with the new address.
- **Resend limitation**: the testing sender without a verified domain may refuse other recipients. A 503 on different address in this environment is expected; verify current email remains unchanged and no token is persisted after failure.
- **Deletion**: use a dedicated local test account (`example.test` can be manually inserted only on localhost if already done). Verify its password, type `DELETE`, approve confirmation. Test that this exact account is removed and personal watches/Discord settings are gone. **Never test irreversible deletion on your real account**.
- Existing public records and sources should remain accessible and unchanged.

## 5. DBeaver (no secrets)

```sql
-- Email change requests: do not select token_hash
SELECT user_id, new_email, requested_at, expires_at
FROM account_email_change_tokens
ORDER BY requested_at DESC;

-- FK deletion rules for tables referencing app_users
SELECT conrelid::regclass AS referencing_table, conname AS constraint_name,
       CASE confdeltype WHEN 'c' THEN 'CASCADE' WHEN 'r' THEN 'RESTRICT'
            WHEN 'a' THEN 'NO ACTION' WHEN 'n' THEN 'SET NULL' ELSE 'OTHER' END AS on_delete
FROM pg_constraint WHERE contype='f' AND confrelid='app_users'::regclass;
```

After intentionally deleting a dedicated test account, verify that exact ID is gone and its watch rules/Discord row are absent. Never query/export `password_hash`, `token_hash`, `webhook_secret`, or `.env`.

## 6. Security follow-ups before deployment

- Globally invalidate prior HTTP sessions after password/email updates, deletion; current-session invalidation alone is not sufficient for internet use.
- Protect deletion and email operations with application- and IP-based rate limits, audit logging without PII/tokens, and automated cleanup for expired tokens.
- Strong email delivery reliability with outbox retry and explicit retention policy; use verified sending domain for actual users.
- Automatic integration tests with PostgreSQL and multiple concurrent sessions; this local patch has not been run against the user's actual Docker or Resend account.
