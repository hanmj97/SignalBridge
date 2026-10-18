-- Phase 13: one-use password recovery tokens. No modifications to existing accounts.
-- Run ONLY against the signalbridge database after backing up the DB.
BEGIN;
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_password_reset_expiry CHECK (expires_at > issued_at)
);
CREATE INDEX IF NOT EXISTS idx_password_reset_expires_at
  ON password_reset_tokens(expires_at);
COMMIT;
