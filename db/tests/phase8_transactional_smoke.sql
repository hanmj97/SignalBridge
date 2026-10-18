-- Transactional smoke test: creates fake source/version/watch only inside BEGIN ... ROLLBACK.
-- No Discord network calls; no permanent rows after this test.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO sources (source_key, label, provider, resource)
VALUES ('phase8_ephemeral_probe', 'Phase8 Probe', 'github_releases', 'probe/demo');
INSERT INTO watch_rules (keyword, source_id, created_at)
VALUES ('AlertProbe', (SELECT id FROM sources WHERE source_key='phase8_ephemeral_probe'), now() - interval '2 minutes');
INSERT INTO records (source_id, external_id, title, url, payload, content_hash)
VALUES ((SELECT id FROM sources WHERE source_key='phase8_ephemeral_probe'),
        'phase8-one', 'AlertProbe release', 'https://github.com/probe/demo/releases/tag/1',
        '{"title":"AlertProbe release"}'::jsonb, repeat('a',64));
INSERT INTO record_versions (record_id, version, change_type, changed_fields, content_hash, payload)
VALUES ((SELECT id FROM records WHERE external_id='phase8-one' AND source_id =
         (SELECT id FROM sources WHERE source_key='phase8_ephemeral_probe')),
        1, 'NEW', ARRAY['title'], repeat('a',64), '{"title":"AlertProbe release"}'::jsonb);

-- Run the production matching logic twice to check idempotency.
INSERT INTO watch_events (rule_id, record_version_id)
SELECT w.id, v.id FROM watch_rules w
JOIN record_versions v ON v.detected_at > w.created_at
JOIN records rec ON rec.id = v.record_id
JOIN sources s ON s.id=rec.source_id
WHERE w.enabled AND (w.source_id IS NULL OR w.source_id=rec.source_id)
  AND (strpos(lower(coalesce(v.payload->>'title',rec.title)),lower(w.keyword)) > 0
       OR strpos(lower(s.label),lower(w.keyword)) > 0)
ON CONFLICT (rule_id,record_version_id) DO NOTHING;
INSERT INTO watch_events (rule_id, record_version_id)
SELECT w.id, v.id FROM watch_rules w
JOIN record_versions v ON v.detected_at > w.created_at
JOIN records rec ON rec.id = v.record_id
JOIN sources s ON s.id=rec.source_id
WHERE w.enabled AND (w.source_id IS NULL OR w.source_id=rec.source_id)
  AND (strpos(lower(coalesce(v.payload->>'title',rec.title)),lower(w.keyword)) > 0
       OR strpos(lower(s.label),lower(w.keyword)) > 0)
ON CONFLICT (rule_id,record_version_id) DO NOTHING;
DO $$ BEGIN
  IF (SELECT COUNT(*) FROM watch_events e JOIN watch_rules w ON w.id=e.rule_id
      WHERE w.keyword='AlertProbe') <> 1 THEN
    RAISE EXCEPTION 'Phase8 idempotency smoke test failed';
  END IF;
END $$;
SELECT 'Phase8 transactional match test OK (changes will be rolled back)' AS result;
ROLLBACK;
