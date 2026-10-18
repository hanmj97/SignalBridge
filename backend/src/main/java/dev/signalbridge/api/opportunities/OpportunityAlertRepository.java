package dev.signalbridge.api.opportunities;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class OpportunityAlertRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public OpportunityAlertRepository(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
  public int count(long uid) {return jdbc.queryForObject("SELECT COUNT(*) FROM opportunity_alert_rules WHERE user_id=:uid",Map.of("uid",uid),Integer.class);}
  private static Instant time(ResultSet rs,String col)throws SQLException {Timestamp t=rs.getTimestamp(col);return t==null?null:t.toInstant();}
  private static OpportunityAlertController.Rule rule(ResultSet rs,int index)throws SQLException {
    Number age=(Number)rs.getObject("age");
    return new OpportunityAlertController.Rule(rs.getLong("id"),rs.getString("keyword"),rs.getString("kind"),
      rs.getString("region"),age==null?null:age.intValue(),rs.getBoolean("enabled"),time(rs,"created_at"));
  }
  public List<OpportunityAlertController.Rule> rules(long uid) {return jdbc.query("""
    SELECT id,keyword,kind,region,age,enabled,created_at FROM opportunity_alert_rules
    WHERE user_id=:uid ORDER BY id DESC
    """,Map.of("uid",uid),OpportunityAlertRepository::rule);}
  public OpportunityAlertController.Rule create(long uid,String keyword,String kind,String region,Integer age){
    var args=new MapSqlParameterSource("uid",uid).addValue("keyword",keyword).addValue("kind",kind)
      .addValue("region",region).addValue("age",age);
    return jdbc.queryForObject("""
      INSERT INTO opportunity_alert_rules(user_id,keyword,kind,region,age)
      VALUES(:uid,:keyword,:kind,:region,:age)
      RETURNING id,keyword,kind,region,age,enabled,created_at
      """,args,OpportunityAlertRepository::rule);
  }
  public boolean toggle(long uid,long id,boolean enabled){return jdbc.update("""
    UPDATE opportunity_alert_rules SET enabled=:enabled WHERE id=:id AND user_id=:uid
    """,Map.of("enabled",enabled,"id",id,"uid",uid))>0;}
  public boolean delete(long uid,long id){return jdbc.update("DELETE FROM opportunity_alert_rules WHERE id=:id AND user_id=:uid",
    Map.of("id",id,"uid",uid))>0;}
  public List<OpportunityAlertController.Event> events(long uid){return jdbc.query("""
    SELECT e.id,e.rule_id,e.catalog_record_id,e.title,e.source_label,e.change_type,
           e.status,e.detected_at,e.delivered_at
    FROM opportunity_alert_events e
    JOIN opportunity_alert_rules r ON r.id=e.rule_id AND r.user_id=:uid
    ORDER BY e.id DESC LIMIT 30
    """,Map.of("uid",uid),(rs,i)->new OpportunityAlertController.Event(rs.getLong("id"),
      rs.getLong("rule_id"),rs.getLong("catalog_record_id"),rs.getString("title"),
      rs.getString("source_label"),rs.getString("change_type"),rs.getString("status"),
      time(rs,"detected_at"),time(rs,"delivered_at")));
  }
}
