# Phase 11 — per-member Discord

## Principle

- The user connects an **official Discord webhook** under `https://discord.com/api/webhooks/…`. The browser only POSTs the URL over the same-origin authenticated session, with CSRF token.
- The Spring Boot service sends exactly one test message and stores the webhook **only if Discord responds 2xx**. The API never returns the URL.
- At rest, the destination is AES-256-GCM encrypted. Storage schema: `member_discord_channels(user_id PK, webhook_secret, activated_at, updated_at, enabled)`.
- The 32-byte Base64 encryption key is shared between Spring Boot (encrypt) and trusted Airflow process (decrypt). Never upload the key/`.env` to GitHub, share it, or rotate it without a key migration. Losing the key makes existing destinations irrecoverable.
- Airflow uses member ID (`watch_rules.user_id = member_discord_channels.user_id`) and excludes `record_versions.detected_at` / `watch_events.created_at` before the channel activation instant; existing pending alerts are not sent in bulk.
- The existing `SIGNALBRIDGE_DISCORD_WEBHOOK_URL` is still for **pipeline failure** alerts; member alerts are strictly separate. Never mix them.
- Error codes in DB are generic; HTTP exception messages from failed webhook URLs are never printed. Discord `allowed_mentions` disables pings.

## Limitations and future hardening

- No guaranteed exactly-once delivery across a network. A crash after Discord accepts a POST but before `SENT` commits can repeat a notification. Event IDs appear in messages for troubleshooting.
- Sending a test message to a webhook causes a real outgoing Discord message.
- Webhook destination remains optional; pending events created before connection are not delivered retroactively, but stay listed as pending in the UI.
- The scheduler sends at most 10 matching events per run. Errors back off to the next DAG run, max 3 attempts except HTTP 429. An admin should investigate `FAILED` statuses.
- Production: HTTPS, reverse proxy, request rate limits, audit logs, secret manager, stable key backup/rotation, separate Airflow secrets, phishing/abuse controls, Discord rate-limit handling, pruning stale events, and deployment isolation still needed.
- One webhook per member for now. To receive alerts in DMs, use a dedicated Discord bot (out of scope).

## DB inspection (never SELECT webhook_secret)

```sql
SELECT u.id, u.nickname, u.email,
       (c.user_id IS NOT NULL) AS discord_connected, c.activated_at
FROM app_users u LEFT JOIN member_discord_channels c ON c.user_id=u.id
ORDER BY u.id;

SELECT w.user_id, e.status, COUNT(*) AS events
FROM watch_events e JOIN watch_rules w ON w.id=e.rule_id
GROUP BY w.user_id, e.status ORDER BY w.user_id, e.status;
```
