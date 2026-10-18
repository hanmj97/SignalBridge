package dev.signalbridge.api.operations;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read-only queries against existing Phase 1 ingestion_runs/sources tables. */
@Repository
public class IngestionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public IngestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<IngestionSnapshot.SourceIngestion> findSourcesWithLatestRun() {
        // LATERAL returns at most one latest run per source; sources with no runs stay visible.
        return jdbc.query("""
                SELECT s.id AS source_id, s.label, s.resource, s.last_checked_at,
                       last_run.id AS latest_run_id, last_run.status AS latest_status,
                       last_run.started_at AS latest_started_at,
                       last_run.finished_at AS latest_finished_at,
                       COALESCE(last_run.fetched_count, 0) AS fetched_count,
                       COALESCE(last_run.new_count, 0) AS new_count,
                       COALESCE(last_run.updated_count, 0) AS updated_count
                FROM sources s
                LEFT JOIN LATERAL (
                    SELECT ir.id, ir.status, ir.started_at, ir.finished_at,
                           ir.fetched_count, ir.new_count, ir.updated_count
                    FROM ingestion_runs ir
                    WHERE ir.source_id = s.id
                    ORDER BY ir.started_at DESC, ir.id DESC
                    LIMIT 1
                ) last_run ON true
                WHERE s.enabled = TRUE AND s.provider = 'github_releases'
                  AND NOT starts_with(s.source_key,'github_user_')
                ORDER BY s.id
                """, Map.of(), (rs, rowNum) -> new IngestionSnapshot.SourceIngestion(
                rs.getLong("source_id"), rs.getString("label"), rs.getString("resource"),
                timestamp(rs, "last_checked_at"), (Long) rs.getObject("latest_run_id"),
                rs.getString("latest_status"), timestamp(rs, "latest_started_at"),
                timestamp(rs, "latest_finished_at"), rs.getInt("fetched_count"),
                rs.getInt("new_count"), rs.getInt("updated_count")
        ));
    }

    public long[] countLast24Hours() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE ir.status IN ('SUCCESS', 'NOT_MODIFIED')) AS completed,
                       COUNT(*) FILTER (WHERE ir.status = 'FAILED') AS failures
                FROM ingestion_runs ir
                JOIN sources s ON s.id = ir.source_id
                WHERE s.enabled = TRUE AND s.provider = 'github_releases'
                  AND NOT starts_with(s.source_key,'github_user_')
                  AND ir.started_at >= now() - interval '24 hours'
                """, Map.of(), (rs, rowNum) -> new long[] {
                rs.getLong("total"), rs.getLong("completed"), rs.getLong("failures")
        });
    }

    public List<IngestionSnapshot.RecentIngestion> findRecentRuns() {
        return jdbc.query("""
                SELECT ir.id, ir.source_id, s.label AS source_label, ir.status,
                       ir.started_at, ir.finished_at,
                       ir.fetched_count, ir.new_count, ir.updated_count
                FROM ingestion_runs ir
                JOIN sources s ON s.id = ir.source_id
                WHERE s.enabled = TRUE AND s.provider = 'github_releases'
                  AND NOT starts_with(s.source_key,'github_user_')
                ORDER BY ir.started_at DESC, ir.id DESC
                LIMIT 12
                """, Map.of(), (rs, rowNum) -> new IngestionSnapshot.RecentIngestion(
                rs.getLong("id"), rs.getLong("source_id"), rs.getString("source_label"),
                rs.getString("status"), timestamp(rs, "started_at"),
                timestamp(rs, "finished_at"), rs.getInt("fetched_count"),
                rs.getInt("new_count"), rs.getInt("updated_count")
        ));
    }

    private static Instant timestamp(ResultSet rs, String field) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(field);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
