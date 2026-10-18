package dev.signalbridge.api.watches;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberDiscordRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public MemberDiscordRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

  public Instant activatedAt(long userId) {
    return jdbc.query("SELECT activated_at FROM member_discord_channels WHERE user_id=:uid AND enabled=TRUE",
      Map.of("uid",userId),rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null);
  }
  public void upsert(long userId,String encrypted) {
    jdbc.update("""
      INSERT INTO member_discord_channels(user_id,webhook_secret,enabled,activated_at,updated_at)
      VALUES (:uid,:secret,TRUE,now(),now())
      ON CONFLICT(user_id) DO UPDATE SET
        webhook_secret=excluded.webhook_secret, enabled=TRUE,
        activated_at=now(), updated_at=now()
      """,Map.of("uid",userId,"secret",encrypted));
  }
  public boolean delete(long userId){return jdbc.update(
    "DELETE FROM member_discord_channels WHERE user_id=:uid",Map.of("uid",userId))>0;}
}
