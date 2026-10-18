package dev.signalbridge.api.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Token hashes are the only token material persisted in PostgreSQL. */
@Repository
public class PasswordResetRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public PasswordResetRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

  /** At most one token per member, and at most one email every 120 seconds. */
  public boolean reserve(long userId, String hash) {
    List<Long> rows=jdbc.query("""
      INSERT INTO password_reset_tokens(user_id,token_hash,issued_at,expires_at)
      VALUES (:userId,:hash,now(),now()+interval '30 minutes')
      ON CONFLICT (user_id) DO UPDATE
      SET token_hash=EXCLUDED.token_hash,
          issued_at=EXCLUDED.issued_at,
          expires_at=EXCLUDED.expires_at
      WHERE password_reset_tokens.issued_at <= now()-interval '120 seconds'
      RETURNING user_id
      """,new MapSqlParameterSource().addValue("userId",userId).addValue("hash",hash),
      (rs,n)->rs.getLong(1));
    return !rows.isEmpty();
  }

  /** Single-use consume; same @Transactional transaction as password update. */
  public Optional<Long> consume(String hash) {
    List<Long> rows=jdbc.query("""
      DELETE FROM password_reset_tokens t USING app_users u
      WHERE t.user_id=u.id AND u.email_verified=TRUE
        AND t.token_hash=:hash AND t.expires_at>now()
      RETURNING t.user_id
      """,Map.of("hash",hash),(rs,n)->rs.getLong(1));
    return rows.stream().findFirst();
  }
}
