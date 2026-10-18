package dev.signalbridge.api.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountProfileRepository {
  public record Profile(long id, String email, String nickname, boolean emailVerified, Instant createdAt) {}
  public record Credentials(String passwordHash) {}

  private final NamedParameterJdbcTemplate jdbc;
  public AccountProfileRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Profile> findProfile(long memberId) {
    return jdbc.query("""
        SELECT id, email, nickname, email_verified, created_at
        FROM app_users WHERE id=:id
        """, Map.of("id",memberId), (rs) -> rs.next() ? Optional.of(new Profile(
          rs.getLong("id"), rs.getString("email"), rs.getString("nickname"),
          rs.getBoolean("email_verified"), rs.getTimestamp("created_at").toInstant())) : Optional.empty());
  }

  public Optional<Credentials> findCredentials(long memberId) {
    return jdbc.query("SELECT password_hash FROM app_users WHERE id=:id", Map.of("id",memberId),
      rs -> rs.next() ? Optional.of(new Credentials(rs.getString(1))) : Optional.empty());
  }

  public boolean updateNickname(long memberId, String nickname) {
    return jdbc.update("UPDATE app_users SET nickname=:nickname WHERE id=:id",
      new MapSqlParameterSource().addValue("id",memberId).addValue("nickname",nickname)) == 1;
  }

  // Compare the current encoded password in the UPDATE condition to prevent stale writes.
  public boolean replacePassword(long memberId, String expectedHash, String nextHash) {
    return jdbc.update("""
      UPDATE app_users SET password_hash=:next
      WHERE id=:id AND password_hash=:expected
      """, new MapSqlParameterSource().addValue("id",memberId)
        .addValue("expected",expectedHash).addValue("next",nextHash)) == 1;
  }
}
