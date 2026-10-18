-- Read-only checks after migration and explicit demo import.
-- Notice is_demo: these records are invented and MUST NOT be served as real policy/job data.
SELECT to_regclass('public.opportunity_details') AS v2_details_table;

SELECT s.provider, d.kind, d.is_demo, COUNT(*) AS records_count
FROM opportunity_details d
JOIN records r ON r.id = d.record_id
JOIN sources s ON s.id = r.source_id
GROUP BY s.provider, d.kind, d.is_demo
ORDER BY s.provider, d.kind;

SELECT r.external_id, r.title, r.current_version, d.kind, d.region_codes,
       d.application_deadline, d.status, d.is_demo
FROM opportunity_details d
JOIN records r ON r.id = d.record_id
WHERE d.is_demo = TRUE
ORDER BY r.external_id;

SELECT r.external_id, v.version, v.change_type, v.changed_fields, v.detected_at
FROM record_versions v
JOIN records r ON r.id = v.record_id
JOIN opportunity_details d ON d.record_id = r.id
WHERE d.is_demo = TRUE
ORDER BY r.external_id, v.version;

-- Existing GitHub source rows must remain present and unaffected.
SELECT provider, COUNT(*) AS source_count FROM sources GROUP BY provider ORDER BY provider;
