-- Engineering V2-10: logged-in member manual GitHub refresh queue.
-- Apply ONLY to signalbridge (member + release DB), after migration 018.
BEGIN;
CREATE TABLE IF NOT EXISTS github_refresh_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);
-- One unfinished request per member and source, even during simultaneous clicks.
CREATE UNIQUE INDEX IF NOT EXISTS idx_github_refresh_active
    ON github_refresh_requests (user_id, source_id)
    WHERE status IN ('PENDING','RUNNING');
CREATE INDEX IF NOT EXISTS idx_github_refresh_pending
    ON github_refresh_requests (requested_at, id)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_github_refresh_member_recent
    ON github_refresh_requests (user_id, source_id, requested_at DESC, id DESC);
COMMIT;
