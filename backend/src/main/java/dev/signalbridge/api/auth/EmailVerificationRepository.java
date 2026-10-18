package dev.signalbridge.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EmailVerificationRepository {
  public record Delivery(long userId, String email) {}
  private final NamedParameterJdbcTemplate jdbc;
  public EmailVerificationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  // Reserve a send window and one hashed, time-limited token in one transaction.
  // A conditional UPDATE protects against simultaneous resend requests across instances.
  @Transactional
  public Optional<Delivery> reserve(String email, String tokenHash) {
    List<Delivery> users=jdbc.query("""
      SELECT id,email FROM app_users WHERE email=:email AND email_verified=FALSE
      """,Map.of("email",email),(rs,n)->new Delivery(rs.getLong("id"),rs.getString("email")));
    if(users.isEmpty()) return Optional.empty();
    Delivery user=users.get(0);
    List<Long> claimed=jdbc.query("""
      UPDATE app_users SET verification_last_sent_at=now()
      WHERE id=:id AND email_verified=FALSE
        AND (verification_last_sent_at IS NULL OR verification_last_sent_at < now() - interval '120 seconds')
      RETURNING id
      """,Map.of("id",user.userId()),(rs,n)->rs.getLong(1));
    if(claimed.isEmpty()) return Optional.empty();
    jdbc.update("DELETE FROM email_verification_tokens WHERE user_id=:id",Map.of("id",user.userId()));
    jdbc.update("""
      INSERT INTO email_verification_tokens (user_id,token_hash,expires_at)
      VALUES (:id,:hash,now() + interval '30 minutes')
      """,Map.of("id",user.userId(),"hash",tokenHash));
    return Optional.of(user);
  }

  @Transactional
  public boolean consumeAndVerify(String tokenHash) {
    List<Long> matching=jdbc.query("""
      UPDATE email_verification_tokens SET consumed_at=now()
      WHERE token_hash=:hash AND consumed_at IS NULL AND expires_at>now()
      RETURNING user_id
      """,Map.of("hash",tokenHash),(rs,n)->rs.getLong(1));
    if(matching.isEmpty()) return false;
    Long id=matching.get(0);
    jdbc.update("UPDATE app_users SET email_verified=TRUE WHERE id=:id",Map.of("id",id));
    jdbc.update("DELETE FROM email_verification_tokens WHERE user_id=:id",Map.of("id",id));
    return true;
  }
}
