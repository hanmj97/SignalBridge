-- Run in DBeaver connected to database "signalbridge".
-- These are READ-ONLY queries and deliberately omit secrets.
SELECT user_id, new_email, requested_at, expires_at
FROM account_email_change_tokens ORDER BY requested_at DESC LIMIT 20;

SELECT conrelid::regclass AS referencing_table, conname,
       CASE confdeltype WHEN 'c' THEN 'CASCADE'
            WHEN 'r' THEN 'RESTRICT'
            WHEN 'a' THEN 'NO ACTION'
            WHEN 'n' THEN 'SET NULL' ELSE 'OTHER' END AS on_delete
FROM pg_constraint WHERE contype='f' AND confrelid='app_users'::regclass
ORDER BY 1;

SELECT (SELECT COUNT(*) FROM app_users) AS member_count,
       (SELECT COUNT(*) FROM watch_rules WHERE user_id IS NOT NULL) AS owned_rules,
       (SELECT COUNT(*) FROM member_discord_channels) AS linked_discord_accounts;
