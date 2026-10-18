package dev.signalbridge.api.repositories;

import dev.signalbridge.api.auth.AccountPrincipal;
import dev.signalbridge.api.dto.ChangeItem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real, DB-backed Engineering dashboard scoped to the logged-in user's subscriptions. */
@RestController
@RequestMapping("/api/v1/my-repositories")
public class MyEngineeringDashboardController {
    private final NamedParameterJdbcTemplate jdbc;

    public record DayCount(String day, long newCount, long updatedCount) {}
    public record Summary(long sourceCount, long recordCount, Instant latestCheckedAt,
                          long changesLast7Days, List<DayCount> days,
                          List<ChangeItem> recentChanges) {}

    public MyEngineeringDashboardController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String VISIBLE = """
        EXISTS (SELECT 1 FROM user_repository_subscriptions u
                JOIN sources s ON s.id=u.source_id
                WHERE u.user_id=:uid AND s.enabled=TRUE
                  AND s.provider='github_releases' AND u.source_id=r.source_id)
        """;
    // Seven calendar days including today, using Korea Standard Time.
    private static final String SEVEN_DAYS = """
        v.detected_at >= ((date_trunc('day', now() AT TIME ZONE 'Asia/Seoul')
                         - interval '6 days') AT TIME ZONE 'Asia/Seoul')
        """;

    private static Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    @GetMapping("/dashboard")
    public Summary dashboard(@AuthenticationPrincipal AccountPrincipal member) {
        var params = Map.<String,Object>of("uid", member.id());
        var counts = jdbc.queryForMap("""
            SELECT COUNT(*) AS source_count, MAX(s.last_checked_at) AS last_checked_at
            FROM user_repository_subscriptions u
            JOIN sources s ON s.id=u.source_id
            WHERE u.user_id=:uid AND s.enabled=TRUE AND s.provider='github_releases'
            """, params);
        long sourceCount = ((Number) counts.get("source_count")).longValue();
        Instant latestChecked = timestamp((Timestamp) counts.get("last_checked_at"));
        Long recordCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM records r WHERE " + VISIBLE, params, Long.class);
        Long changeCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM record_versions v JOIN records r ON r.id=v.record_id
            WHERE
            """ + VISIBLE + " AND " + SEVEN_DAYS, params, Long.class);

        var days = jdbc.query("""
            SELECT to_char(v.detected_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD') AS day,
                   COUNT(*) FILTER (WHERE v.change_type='NEW') AS new_count,
                   COUNT(*) FILTER (WHERE v.change_type='UPDATED') AS updated_count
            FROM record_versions v JOIN records r ON r.id=v.record_id
            WHERE
            """ + VISIBLE + " AND " + SEVEN_DAYS + """
            GROUP BY 1 ORDER BY 1
            """, params, (rs,index)->new DayCount(rs.getString("day"),
            rs.getLong("new_count"),rs.getLong("updated_count")));

        var recent = jdbc.query("""
            SELECT v.id, v.record_id, r.title AS record_title, s.label AS source_label,
                   v.version, v.change_type, v.changed_fields, v.detected_at
            FROM record_versions v JOIN records r ON r.id=v.record_id
            JOIN sources s ON s.id=r.source_id
            WHERE
            """ + VISIBLE + """
            ORDER BY v.detected_at DESC, v.id DESC LIMIT 5
            """, params, (rs,index)->new ChangeItem(
                rs.getLong("id"),rs.getLong("record_id"),rs.getString("record_title"),
                rs.getString("source_label"),rs.getInt("version"),rs.getString("change_type"),
                Arrays.asList((String[])rs.getArray("changed_fields").getArray()),
                timestamp(rs.getTimestamp("detected_at"))));
        return new Summary(sourceCount,recordCount==null?0:recordCount,
                latestChecked,changeCount==null?0:changeCount,days,recent);
    }
}
