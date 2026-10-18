package dev.signalbridge.api.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountLifecycleRepository {
  public record EmailChange(long userId, String newEmail) {}
  private final NamedParameterJdbcTemplate jdbc;
  public AccountLifecycleRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

  /** A single outstanding link per account; throttle to at most once per two minutes. */
  public boolean reserveEmailChange(long userId,String email,String hash){
    List<Long> rows=jdbc.query("""
      INSERT INTO account_email_change_tokens(user_id,new_email,token_hash,requested_at,expires_at)
      VALUES (:id,:email,:hash,now(),now()+interval '30 minutes')
      ON CONFLICT (user_id) DO UPDATE SET
        new_email=EXCLUDED.new_email,token_hash=EXCLUDED.token_hash,
        requested_at=EXCLUDED.requested_at,expires_at=EXCLUDED.expires_at
      WHERE account_email_change_tokens.requested_at <= now()-interval '120 seconds'
      RETURNING user_id
      """,new MapSqlParameterSource().addValue("id",userId)
        .addValue("email",email).addValue("hash",hash),(rs,n)->rs.getLong(1));
    return !rows.isEmpty();
  }

  public Optional<EmailChange> consumeEmailChange(String tokenHash) {
    List<EmailChange> found=jdbc.query("""
      DELETE FROM account_email_change_tokens
      WHERE token_hash=:hash AND expires_at>now()
      RETURNING user_id,new_email
      """,Map.of("hash",tokenHash),(rs,n)->new EmailChange(rs.getLong(1),rs.getString(2)));
    return found.stream().findFirst();
  }

  /** No new identity: retain original id, watch rules, notifications and Discord destination. */
  public boolean changeVerifiedEmail(long id,String newEmail) {
    return jdbc.update("""
      UPDATE app_users SET email=:newEmail,email_verified=TRUE
      WHERE id=:id AND email_verified=TRUE AND email<>:newEmail
      """,new MapSqlParameterSource().addValue("id",id).addValue("newEmail",newEmail))==1;
  }

  /** Invalidate outstanding recovery links issued to the old address. */
  public void revokeOtherEmailTokens(long userId){
    jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=:id", Map.of("id",userId));
    jdbc.update("DELETE FROM email_verification_tokens WHERE user_id=:id", Map.of("id",userId));
  }

  /** FK CASCADE deletes watch_rules/events, Discord destination and recovery tokens. */
  public boolean deleteAccount(long userId,String expectedPasswordHash){
    return jdbc.update("DELETE FROM app_users WHERE id=:id AND password_hash=:hash",
      new MapSqlParameterSource().addValue("id",userId).addValue("hash",expectedPasswordHash))==1;
  }
}
