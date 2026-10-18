-- V2-5B: distinguish source dataset links from verified article permalinks.
-- APPLY TO signalbridge_v2_lab ONLY. Never drops or rewrites tables.
BEGIN;
ALTER TABLE opportunity_details
    ADD COLUMN IF NOT EXISTS link_kind TEXT NOT NULL DEFAULT 'ARTICLE'
    CHECK (link_kind IN ('ARTICLE', 'DATASET'));
COMMENT ON COLUMN opportunity_details.link_kind IS
    'ARTICLE = verified item permalink; DATASET = provider API catalogue page, not an individual posting.';
COMMIT;
