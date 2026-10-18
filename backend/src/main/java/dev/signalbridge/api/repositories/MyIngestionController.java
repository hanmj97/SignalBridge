package dev.signalbridge.api.repositories;

import dev.signalbridge.api.auth.AccountPrincipal;
import dev.signalbridge.api.operations.IngestionSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingestion history for the current member's own GitHub repository subscriptions. */
@RestController
@RequestMapping("/api/v1/my-repositories")
public class MyIngestionController {
    private final NamedParameterJdbcTemplate jdbc;
    public MyIngestionController(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    private static Instant time(Timestamp v) {return v==null?null:v.toInstant();}

    @GetMapping("/ingestions")
    public IngestionSnapshot ingestions(@AuthenticationPrincipal AccountPrincipal member) {
        var p = Map.<String,Object>of("uid",member.id());
        var sources = jdbc.query("""
            SELECT s.id AS source_id,s.label,s.resource,s.last_checked_at,
                   last_run.id AS latest_run_id, last_run.status AS latest_status,
                   last_run.started_at AS latest_started_at,
                   last_run.finished_at AS latest_finished_at,
                   COALESCE(last_run.fetched_count,0) AS fetched_count,
                   COALESCE(last_run.new_count,0) AS new_count,
                   COALESCE(last_run.updated_count,0) AS updated_count
            FROM user_repository_subscriptions u
            JOIN sources s ON s.id=u.source_id
            LEFT JOIN LATERAL (
                SELECT ir.id,ir.status,ir.started_at,ir.finished_at,
                       ir.fetched_count,ir.new_count,ir.updated_count
                FROM ingestion_runs ir WHERE ir.source_id=s.id
                ORDER BY ir.started_at DESC,ir.id DESC LIMIT 1
            ) last_run ON TRUE
            WHERE u.user_id=:uid AND s.enabled=TRUE AND s.provider='github_releases'
            ORDER BY s.id
            """,p,(rs,index)->new IngestionSnapshot.SourceIngestion(
                rs.getLong("source_id"),rs.getString("label"),rs.getString("resource"),
                time(rs.getTimestamp("last_checked_at")),(Long)rs.getObject("latest_run_id"),
                rs.getString("latest_status"),time(rs.getTimestamp("latest_started_at")),
                time(rs.getTimestamp("latest_finished_at")),rs.getInt("fetched_count"),
                rs.getInt("new_count"),rs.getInt("updated_count")));

        var stats = jdbc.queryForMap("""
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE ir.status IN ('SUCCESS','NOT_MODIFIED')) AS completed,
                   COUNT(*) FILTER (WHERE ir.status='FAILED') AS failures
            FROM ingestion_runs ir JOIN sources s ON s.id=ir.source_id
            WHERE s.enabled=TRUE AND s.provider='github_releases'
              AND ir.started_at >= now() - interval '24 hours'
              AND EXISTS(SELECT 1 FROM user_repository_subscriptions u
                         WHERE u.user_id=:uid AND u.source_id=s.id)
            """,p);
        var recent = jdbc.query("""
            SELECT ir.id,ir.source_id,s.label AS source_label,ir.status,ir.started_at,
                   ir.finished_at,ir.fetched_count,ir.new_count,ir.updated_count
            FROM ingestion_runs ir JOIN sources s ON s.id=ir.source_id
            WHERE s.enabled=TRUE AND s.provider='github_releases'
              AND EXISTS(SELECT 1 FROM user_repository_subscriptions u
                         WHERE u.user_id=:uid AND u.source_id=s.id)
            ORDER BY ir.started_at DESC,ir.id DESC LIMIT 12
            """,p,(rs,index)->new IngestionSnapshot.RecentIngestion(
                rs.getLong("id"),rs.getLong("source_id"),rs.getString("source_label"),
                rs.getString("status"),time(rs.getTimestamp("started_at")),
                time(rs.getTimestamp("finished_at")),rs.getInt("fetched_count"),
                rs.getInt("new_count"),rs.getInt("updated_count")));
        return new IngestionSnapshot(Instant.now(),sources.size(),
            ((Number)stats.get("total")).longValue(),
            ((Number)stats.get("completed")).longValue(),
            ((Number)stats.get("failures")).longValue(),sources,recent);
    }
}
