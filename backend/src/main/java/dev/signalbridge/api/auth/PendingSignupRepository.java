package dev.signalbridge.api.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** A pending signup is NOT a user and grants no login or watchlist access. */
@Repository
public class PendingSignupRepository {
  public record Confirmed(String email,String nickname,String passwordHash) {}
  private final NamedParameterJdbcTemplate jdbc;
  public PendingSignupRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

  // Re-register only after the old 30-minute link expired. Never replace an
  // active signup, as that could unexpectedly change its password or nickname.
  public boolean create(String email,String nickname,String passwordHash,String tokenHash) {
    List<String> created=jdbc.query("""
      INSERT INTO pending_signups (email,nickname,password_hash,token_hash,expires_at,last_sent_at)
      VALUES (:email,:nickname,:hash,:token,now()+interval '30 minutes',now())
      ON CONFLICT (email) DO UPDATE SET
        nickname=EXCLUDED.nickname,
        password_hash=EXCLUDED.password_hash,
        token_hash=EXCLUDED.token_hash,
        expires_at=EXCLUDED.expires_at,
        last_sent_at=now()
      WHERE pending_signups.expires_at <= now()
      RETURNING email
      """,new MapSqlParameterSource().addValue("email",email).addValue("nickname",nickname)
        .addValue("hash",passwordHash).addValue("token",tokenHash),
      (rs,n)->rs.getString(1));
    return !created.isEmpty();
  }

  // Reserve another one-time token. Joined to the caller's transaction: if
  // Resend rejects delivery, the updated token/cooldown are rolled back.
  public Optional<String> reserveResend(String email,String tokenHash) {
    List<String> rows=jdbc.query("""
      UPDATE pending_signups
      SET token_hash=:hash,last_sent_at=now(),expires_at=now()+interval '30 minutes'
      WHERE email=:email AND expires_at>now()
        AND last_sent_at <= now()-interval '120 seconds'
      RETURNING email
      """,Map.of("email",email,"hash",tokenHash),(rs,n)->rs.getString(1));
    return rows.stream().findFirst();
  }

  // Take the token exactly once and create the verified account atomically.
  // The outer @Transactional in EmailVerificationService rolls back the DELETE
  // if inserting the actual member ever fails (e.g. conflicting email).
  public Optional<Confirmed> consume(String tokenHash) {
    List<Confirmed> rows=jdbc.query("""
      DELETE FROM pending_signups
      WHERE token_hash=:hash AND expires_at>now()
      RETURNING email,nickname,password_hash
      """,Map.of("hash",tokenHash),
      (rs,n)->new Confirmed(rs.getString(1),rs.getString(2),rs.getString(3)));
    return rows.stream().findFirst();
  }
}
