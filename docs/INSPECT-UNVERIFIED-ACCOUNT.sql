-- Execute in DBeaver (signalbridge). Replace the example address with the typo.
-- These queries do not change any data.
SELECT id,email,nickname,email_verified,created_at
FROM app_users WHERE email='mistyped@example.com';

SELECT wr.id,wr.keyword,wr.enabled
FROM watch_rules wr JOIN app_users a ON a.id=wr.user_id
WHERE a.email='mistyped@example.com';

SELECT t.id,t.created_at,t.expires_at
FROM email_verification_tokens t JOIN app_users a ON a.id=t.user_id
WHERE a.email='mistyped@example.com';

-- Only after confirming that the record is an unwanted local test account,
-- unverified, AND has no watchlist rules, you can perform a targeted cleanup:
-- BEGIN;
-- DELETE FROM app_users u
-- WHERE u.email='mistyped@example.com' AND u.email_verified=FALSE
--   AND NOT EXISTS (SELECT 1 FROM watch_rules wr WHERE wr.user_id=u.id)
-- RETURNING u.id,u.email;
-- COMMIT;
-- This deletes only the inspected unverified account; tokens referencing it
-- are removed through the existing ON DELETE CASCADE FK.
