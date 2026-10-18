-- Phase 10 hotfix: registrations are temporary until email ownership is proven.
-- Safe for existing Phase 9/10 users, watch rules, tokens and Airflow.
BEGIN;
CREATE TABLE IF NOT EXISTS pending_signups (
  email TEXT PRIMARY KEY,
  nickname VARCHAR(40) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  last_sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_pending_signup_email_lower CHECK (email=lower(btrim(email)))
);
CREATE INDEX IF NOT EXISTS idx_pending_signups_expires ON pending_signups(expires_at);
COMMIT;
