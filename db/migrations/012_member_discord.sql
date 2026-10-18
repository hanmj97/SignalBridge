-- Phase 11: each verified member owns one Discord destination.
-- Does not change existing member, watch, version or ingestion data.
BEGIN;
CREATE TABLE IF NOT EXISTS member_discord_channels (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    webhook_secret TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMIT;
