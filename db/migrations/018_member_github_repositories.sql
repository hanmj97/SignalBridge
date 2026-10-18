-- Engineering V2-9: private user/source associations. Apply to signalbridge (NOT signalbridge_v2_lab).
BEGIN;
CREATE TABLE IF NOT EXISTS user_repository_subscriptions (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, source_id)
);
CREATE INDEX IF NOT EXISTS idx_user_repo_source ON user_repository_subscriptions(source_id);
COMMIT;
