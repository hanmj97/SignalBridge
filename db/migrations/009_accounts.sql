-- Phase 9: transactional, repeatable migration. Back up the DB first.
-- Preserves prior rules/events, but prevents unowned legacy rules being delivered.
BEGIN;
CREATE TABLE IF NOT EXISTS app_users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  nickname VARCHAR(40) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_app_users_email_lower CHECK (email = lower(btrim(email)))
);
ALTER TABLE watch_rules ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES app_users(id) ON DELETE CASCADE;
-- Legacy, previously shared watch rules remain stored for backup/manual migration only.
UPDATE watch_rules SET enabled = FALSE WHERE user_id IS NULL AND enabled = TRUE;
-- Disallow reactivating any legacy unowned rule.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='watch_rule_owner_or_disabled') THEN
    ALTER TABLE watch_rules ADD CONSTRAINT watch_rule_owner_or_disabled CHECK (user_id IS NOT NULL OR enabled = FALSE);
  END IF;
END $$;
-- Global uniqueness previously prevented two users from tracking the same word.
DROP INDEX IF EXISTS uq_watch_rules_keyword_source;
CREATE UNIQUE INDEX IF NOT EXISTS uq_watch_rules_user_keyword_source
  ON watch_rules (user_id, (lower(btrim(keyword))), source_id) NULLS NOT DISTINCT
  WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_watch_rules_owner_created
  ON watch_rules (user_id, created_at DESC, id DESC);
COMMIT;
