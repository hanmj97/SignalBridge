package dev.signalbridge.api.watches;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WatchRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public WatchRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
  public boolean sourceExists(long userId,long id) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
      """
      SELECT EXISTS(SELECT 1 FROM sources s
         WHERE s.id=:id AND s.enabled=TRUE AND s.provider='github_releases'
         AND (NOT starts_with(s.source_key,'github_user_')
         OR EXISTS (SELECT 1 FROM user_repository_subscriptions u WHERE u.user_id=:uid AND u.source_id=s.id)))
      """,
      Map.of("id",id,"uid",userId),Boolean.class));
  }
  public int countForUser(long userId) {return jdbc.queryForObject(
    "SELECT COUNT(*) FROM watch_rules WHERE user_id=:uid", Map.of("uid",userId),Integer.class);}
  public List<WatchDtos.WatchRule> all(long uid) {return jdbc.query("""
    SELECT w.id,w.keyword,w.source_id,s.label AS source_label,w.enabled,w.created_at
    FROM watch_rules w LEFT JOIN sources s ON s.id=w.source_id
    WHERE w.user_id=:uid ORDER BY w.created_at DESC,w.id DESC
    """,Map.of("uid",uid),(rs,row)->mapRule(rs));}
  public WatchDtos.WatchRule create(long uid,String keyword,Long sourceId) {
    MapSqlParameterSource p=new MapSqlParameterSource()
      .addValue("uid",uid).addValue("keyword",keyword).addValue("sourceId",sourceId);
    return jdbc.queryForObject("""
      WITH added AS(INSERT INTO watch_rules(user_id,keyword,source_id) VALUES(:uid,:keyword,:sourceId)
        RETURNING id,keyword,source_id,enabled,created_at)
      SELECT a.id,a.keyword,a.source_id,s.label AS source_label,a.enabled,a.created_at
      FROM added a LEFT JOIN sources s ON s.id=a.source_id
      """,p,(rs,row)->mapRule(rs));
  }
  public boolean setEnabled(long uid,long id,boolean enabled) {return jdbc.update(
    "UPDATE watch_rules SET enabled=:enabled WHERE id=:id AND user_id=:uid",
    Map.of("enabled",enabled,"id",id,"uid",uid))>0;}
  public boolean delete(long uid,long id) {return jdbc.update(
    "DELETE FROM watch_rules WHERE id=:id AND user_id=:uid", Map.of("id",id,"uid",uid))>0;}
  public List<WatchDtos.WatchEvent> recentEvents(long uid) {return jdbc.query("""
    SELECT e.id,e.rule_id,w.keyword,r.id AS record_id,v.id AS version_id,
      COALESCE(v.payload->>'title',r.title) AS title,s.label AS source_label,
      r.url,v.change_type,e.status,v.detected_at,e.delivered_at
    FROM watch_events e
    JOIN watch_rules w ON w.id=e.rule_id AND w.user_id=:uid
    JOIN record_versions v ON v.id=e.record_version_id
    JOIN records r ON r.id=v.record_id
    JOIN sources s ON s.id=r.source_id
    ORDER BY e.id DESC LIMIT 20
    """,Map.of("uid",uid),(rs,row)->new WatchDtos.WatchEvent(
      rs.getLong("id"),rs.getLong("rule_id"),rs.getString("keyword"),rs.getLong("record_id"),
      rs.getLong("version_id"),rs.getString("title"),rs.getString("source_label"),
      rs.getString("url"),rs.getString("change_type"),rs.getString("status"),
      time(rs,"detected_at"),time(rs,"delivered_at")));}
  private static WatchDtos.WatchRule mapRule(ResultSet rs)throws SQLException{
    Object source=rs.getObject("source_id");
    return new WatchDtos.WatchRule(rs.getLong("id"),rs.getString("keyword"),
      source==null?null:((Number)source).longValue(),rs.getString("source_label"),
      rs.getBoolean("enabled"),time(rs,"created_at"));
  }
  private static Instant time(ResultSet rs,String field)throws SQLException{
    Timestamp t=rs.getTimestamp(field);return t==null?null:t.toInstant();}
}
