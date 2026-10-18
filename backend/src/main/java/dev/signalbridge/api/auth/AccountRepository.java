package dev.signalbridge.api.auth;

import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public AccountRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
  private static final org.springframework.jdbc.core.RowMapper<Account> MAPPER = (rs,n) ->
    new Account(rs.getLong("id"), rs.getString("email"), rs.getString("nickname"),
      rs.getString("password_hash"), rs.getTimestamp("created_at").toInstant(),
      rs.getBoolean("email_verified"));
  public Optional<Account> findByEmail(String email) {
    try { return Optional.of(jdbc.queryForObject(
      "SELECT id,email,nickname,password_hash,created_at,email_verified FROM app_users WHERE email=:email",
      Map.of("email",email), MAPPER)); }
    catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
  }
  public Account insert(String email,String nickname,String hash) {
    return jdbc.queryForObject("""
      INSERT INTO app_users (email,nickname,password_hash,email_verified)
      VALUES (:email,:nickname,:hash,FALSE)
      RETURNING id,email,nickname,password_hash,created_at,email_verified
      """,new MapSqlParameterSource().addValue("email",email).addValue("nickname",nickname).addValue("hash",hash), MAPPER);
  }
  // Only the confirmed owner of an email address becomes an actual account.
  public void insertVerified(String email,String nickname,String hash) {
    jdbc.update("""
      INSERT INTO app_users (email,nickname,password_hash,email_verified)
      VALUES (:email,:nickname,:hash,TRUE)
      """,new MapSqlParameterSource().addValue("email",email)
         .addValue("nickname",nickname).addValue("hash",hash));
  }
}
