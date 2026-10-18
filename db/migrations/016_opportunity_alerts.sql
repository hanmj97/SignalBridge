-- V2-3: member-owned opportunity subscriptions and notification outbox.
-- Apply to signalbridge (accounts DB), NOT signalbridge_v2_lab.
-- No changes to existing watch_rules or member Discord destinations.
BEGIN;
CREATE TABLE IF NOT EXISTS opportunity_alert_rules (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  keyword TEXT,
  kind TEXT CHECK (kind IS NULL OR kind IN ('POLICY','BENEFIT','JOB','PROGRAM')),
  region TEXT,
  age SMALLINT CHECK (age IS NULL OR age BETWEEN 0 AND 100),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (keyword IS NOT NULL OR kind IS NOT NULL OR region IS NOT NULL OR age IS NOT NULL),
  CHECK (keyword IS NULL OR (char_length(keyword) BETWEEN 2 AND 80 AND keyword=btrim(keyword))),
  CHECK (region IS NULL OR (char_length(region) BETWEEN 1 AND 40 AND region=btrim(region)))
);
CREATE INDEX IF NOT EXISTS idx_opportunity_alert_rules_user
  ON opportunity_alert_rules(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_opportunity_alert_rules_config
  ON opportunity_alert_rules(user_id, (lower(coalesce(keyword,''))),
    (coalesce(kind,'')), (coalesce(region,'')), (coalesce(age,-1)));
CREATE TABLE IF NOT EXISTS opportunity_alert_events (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_id BIGINT NOT NULL REFERENCES opportunity_alert_rules(id) ON DELETE CASCADE,
  -- External reference to V2 catalog DB (cross-database FK is impossible).
  catalog_version_id BIGINT NOT NULL,
  catalog_record_id BIGINT NOT NULL,
  title TEXT NOT NULL,
  source_label TEXT NOT NULL,
  change_type TEXT NOT NULL CHECK (change_type IN ('NEW','UPDATED')),
  detected_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','SENT','FAILED')),
  attempts INTEGER NOT NULL DEFAULT 0,
  error_code TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_attempt_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  UNIQUE(rule_id,catalog_version_id)
);
CREATE INDEX IF NOT EXISTS idx_opportunity_alert_events_pending
  ON opportunity_alert_events(status,id) WHERE status='PENDING';
COMMIT;
