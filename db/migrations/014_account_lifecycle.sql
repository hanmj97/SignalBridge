-- Phase 14: authenticated email changes and account-deletion support.
-- Existing account/watch/Discord data is preserved; no bulk deletion.
BEGIN;
CREATE TABLE IF NOT EXISTS account_email_change_tokens (
  user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  new_email TEXT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_account_email_change_lower CHECK(new_email = lower(btrim(new_email))),
  CONSTRAINT chk_account_email_change_expiry CHECK(expires_at > requested_at)
);
CREATE INDEX IF NOT EXISTS idx_email_change_expiry ON account_email_change_tokens(expires_at);
COMMIT;
