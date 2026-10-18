package dev.signalbridge.api.repository;

import dev.signalbridge.api.dto.ApiPage;
import dev.signalbridge.api.dto.ChangeItem;
import dev.signalbridge.api.dto.RecordDetail;
import dev.signalbridge.api.dto.RecordSummary;
import dev.signalbridge.api.dto.SourceItem;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SearchStrategy searchStrategy;

    private enum SearchStrategy { OR, UNION }

    public CatalogRepository(NamedParameterJdbcTemplate jdbc,
                             @Value("${signalbridge.search.strategy:OR}") String configuredStrategy) {
        this.jdbc = jdbc;
        try {
            this.searchStrategy = SearchStrategy.valueOf(configuredStrategy.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("signalbridge.search.strategy must be OR or UNION", exception);
        }
    }

    public ApiPage<RecordSummary> findRecords(int page, int size, String q, Long sourceId) {
        return queryRecords(page, size, q, sourceId, null);
    }

    public ApiPage<RecordSummary> findMyRecords(int page, int size, String q, Long sourceId, long userId) {
        return queryRecords(page, size, q, sourceId, userId);
    }

    private ApiPage<RecordSummary> queryRecords(int page, int size, String q, Long sourceId, Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (userId != null) params.addValue("userId", userId);
        boolean searching = q != null && !q.isBlank();
        if (sourceId != null) {
            params.addValue("sourceId", sourceId);
        }
        if (searching) {
            // %, _, and ! are literal search text, not SQL LIKE wildcard characters.
            String escaped = q.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("query", "%" + escaped + "%");
        }

        final String from = " FROM records r JOIN sources s ON s.id = r.source_id";
        final String membershipGuard = userId == null
                ? " AND NOT EXISTS (SELECT 1 FROM sources hidden WHERE hidden.id=r.source_id AND starts_with(hidden.source_key,'github_user_'))"
                : " AND EXISTS (SELECT 1 FROM user_repository_subscriptions urs WHERE urs.user_id=:userId AND urs.source_id=r.source_id)";
        // last_seen_at changes after every successful poll, even when a release is unchanged.
        // Use the most recent persisted NEW/UPDATED version instead.
        final String latestChangeJoin = " " + """
                LEFT JOIN LATERAL (
                    SELECT v.detected_at FROM record_versions v
                    WHERE v.record_id = r.id
                    ORDER BY v.version DESC LIMIT 1
                ) last_change ON TRUE
                """;
        // Sort by original GitHub publication time, not bulk import time. For equal publication
        // timestamps, compare SemVer numeric components (13.2.4 > 13.1.4 > 12.4.9).
        // Across repositories sort by publication time. With a source selected, sort by
        // numeric release version (Grafana 13.2.4 before 13.1.4) then publication date.
        final String releaseOrder = sourceId == null
                ? publicationOrder("r.payload", "last_change.detected_at") + ", " + semverOrder("r.payload")
                : semverOrder("r.payload") + ", " + publicationOrder("r.payload", "last_change.detected_at");
        final String orderLimit = " ORDER BY " + releaseOrder + ", r.id DESC LIMIT :size OFFSET :offset";
        final String columns = """
                SELECT r.id, r.source_id, s.label AS source_label, r.title, r.url,
                       r.payload->>'tag_name' AS tag_name,
                       r.payload->>'published_at' AS published_at,
                       r.current_version, r.first_seen_at, r.last_seen_at,
                       COALESCE(last_change.detected_at, r.first_seen_at) AS latest_changed_at
                """;

        String countSql;
        String pageSql;
        if (searching && searchStrategy == SearchStrategy.UNION) {
            // Each UNION branch applies the source filter before searching.
            // UNION (not UNION ALL) removes duplicate IDs when both conditions match.
            String guard = (sourceId == null ? "" : " AND r.source_id = :sourceId") + membershipGuard;
            String matchedIds = """
                    SELECT r.id FROM records r
                    WHERE r.title ILIKE :query ESCAPE '!'
                    """ + guard + " UNION " + """
                    SELECT r.id FROM records r JOIN sources s ON s.id = r.source_id
                    WHERE s.label ILIKE :query ESCAPE '!'
                    """ + guard;
            countSql = "SELECT COUNT(*) FROM (" + matchedIds + ") matched";
            pageSql = columns + from + " JOIN (" + matchedIds + ") matched ON matched.id = r.id" + latestChangeJoin + orderLimit;
        } else {
            // Keep the original OR SQL for no-query requests and as the safe default.
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            if (sourceId != null) where.append(" AND r.source_id = :sourceId");
            if (searching) where.append(" AND (r.title ILIKE :query ESCAPE '!' OR s.label ILIKE :query ESCAPE '!')");
            where.append(membershipGuard);
            countSql = "SELECT COUNT(*)" + from + where;
            pageSql = columns + from + latestChangeJoin + where + orderLimit;
        }

        Long count = jdbc.queryForObject(countSql, params, Long.class);
        long total = count == null ? 0L : count;
        params.addValue("size", size);
        params.addValue("offset", (long) page * size);
        List<RecordSummary> items = jdbc.query(pageSql, params, RECORD_MAPPER);
        return ApiPage.of(items, page, size, total);
    }

    public Optional<RecordDetail> findRecord(long id) {
        String sql = """
                SELECT r.id, r.source_id, s.label AS source_label, r.title, r.url,
                       r.payload->>'tag_name' AS tag_name,
                       r.payload->>'published_at' AS published_at,
                       r.payload->>'body' AS description,
                       COALESCE((r.payload->>'prerelease')::boolean, false) AS prerelease,
                       r.current_version, r.first_seen_at, r.last_seen_at
                FROM records r JOIN sources s ON s.id = r.source_id
                WHERE r.id = :id
                """;
        List<RecordDetail> rows = jdbc.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) ->
                new RecordDetail(
                        rs.getLong("id"), rs.getLong("source_id"), rs.getString("source_label"),
                        rs.getString("title"), rs.getString("url"), rs.getString("tag_name"),
                        rs.getString("published_at"), rs.getString("description"),
                        rs.getBoolean("prerelease"), rs.getInt("current_version"),
                        instant(rs, "first_seen_at"), instant(rs, "last_seen_at")
                ));
        return rows.stream().findFirst();
    }

    public ApiPage<ChangeItem> findChanges(int page, int size, Long sourceId) {
        return queryChanges(page, size, sourceId, null);
    }

    public ApiPage<ChangeItem> findMyChanges(int page, int size, Long sourceId, long userId) {
        return queryChanges(page, size, sourceId, userId);
    }

    private ApiPage<ChangeItem> queryChanges(int page, int size, Long sourceId, Long userId) {
        String from = " FROM record_versions v JOIN records r ON r.id=v.record_id JOIN sources s ON s.id=r.source_id";
        String where = " WHERE 1=1" + (sourceId == null ? "" : " AND r.source_id = :sourceId")
                + (userId == null
                    ? " AND NOT EXISTS (SELECT 1 FROM sources hidden WHERE hidden.id=r.source_id AND starts_with(hidden.source_key,'github_user_'))"
                    : " AND EXISTS (SELECT 1 FROM user_repository_subscriptions urs WHERE urs.user_id=:userId AND urs.source_id=r.source_id)");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (userId != null) params.addValue("userId", userId);
        if (sourceId != null) {
            params.addValue("sourceId", sourceId);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + from + where, params, Long.class);
        long total = count == null ? 0 : count;
        params.addValue("size", size);
        params.addValue("offset", (long) page * size);
        String sql = """
                SELECT v.id, v.record_id, r.title AS record_title, s.label AS source_label,
                       v.version, v.change_type, v.changed_fields, v.detected_at
                """ + from + where + " ORDER BY "
                + (sourceId == null
                    ? publicationOrder("v.payload", "v.detected_at") + ", " + semverOrder("v.payload")
                    : semverOrder("v.payload") + ", " + publicationOrder("v.payload", "v.detected_at"))
                + ", v.id DESC LIMIT :size OFFSET :offset";
        List<ChangeItem> items = jdbc.query(sql, params, (rs, rowNum) -> new ChangeItem(
                rs.getLong("id"), rs.getLong("record_id"), rs.getString("record_title"),
                rs.getString("source_label"), rs.getInt("version"), rs.getString("change_type"),
                Arrays.asList((String[]) rs.getArray("changed_fields").getArray()),
                instant(rs, "detected_at")
        ));
        return ApiPage.of(items, page, size, total);
    }

    // The existing UNIQUE(record_id, version) index supports an ordered per-record lookup.
    // We deliberately return the latest 20 versions to bound response size.
    public List<ChangeItem> findRecordHistory(long recordId) {
        String sql = """
                SELECT v.id, v.record_id, r.title AS record_title, s.label AS source_label,
                       v.version, v.change_type, v.changed_fields, v.detected_at
                FROM record_versions v
                JOIN records r ON r.id = v.record_id
                JOIN sources s ON s.id = r.source_id
                WHERE v.record_id = :recordId
                ORDER BY v.detected_at DESC NULLS LAST, v.id DESC
                LIMIT 20
                """;
        return jdbc.query(sql, new MapSqlParameterSource("recordId", recordId), (rs, rowNum) -> new ChangeItem(
                rs.getLong("id"), rs.getLong("record_id"), rs.getString("record_title"),
                rs.getString("source_label"), rs.getInt("version"), rs.getString("change_type"),
                Arrays.asList((String[]) rs.getArray("changed_fields").getArray()),
                instant(rs, "detected_at")
        ));
    }

    public List<SourceItem> findSources() {
        return jdbc.query("""
                SELECT s.id, s.source_key, s.label, s.provider, s.resource, s.last_checked_at,
                       COUNT(r.id) AS record_count
                FROM sources s
                LEFT JOIN records r ON r.source_id=s.id
                WHERE s.enabled = true AND NOT starts_with(s.source_key,'github_user_')
                GROUP BY s.id
                ORDER BY s.id ASC
                """, (rs, rowNum) -> new SourceItem(
                rs.getLong("id"), rs.getString("source_key"), rs.getString("label"),
                rs.getString("provider"), rs.getString("resource"),
                instant(rs, "last_checked_at"), rs.getLong("record_count")
        ));
    }


    public List<SourceItem> findMySources(long userId) {
        return jdbc.query("""
                SELECT s.id, s.source_key, s.label, s.provider, s.resource, s.last_checked_at,
                       COUNT(r.id) AS record_count
                FROM user_repository_subscriptions u
                JOIN sources s ON s.id=u.source_id
                LEFT JOIN records r ON r.source_id=s.id
                WHERE u.user_id=:userId
                GROUP BY s.id
                ORDER BY s.label ASC
                """, new MapSqlParameterSource("userId", userId), (rs, i) -> new SourceItem(
                rs.getLong("id"), rs.getString("source_key"), rs.getString("label"),
                rs.getString("provider"), rs.getString("resource"),
                instant(rs, "last_checked_at"), rs.getLong("record_count")));
    }

    // 'published_at' only comes from our normalized GitHub payload; cast is restricted to ISO format.
    // The fallback reflects the detection time for records without a published release date.
    static String publicationOrder(String payload, String fallback) {
        String published = "(" + payload + "->>'published_at')";
        return "COALESCE(CASE WHEN " + published + " ~ '^\\d{4}-\\d{2}-\\d{2}T' "
                + "THEN " + published + "::timestamptz END, " + fallback + ") DESC NULLS LAST";
    }

    // Numeric semver sorting. Prevent lexical sorting where 13.1.10 < 13.1.9.
    static String semverOrder(String payload) {
        String tag = "COALESCE(" + payload + "->>'tag_name','')";
        String numeric = "substring(" + tag + " from '^[vV]?([0-9]+(\\.[0-9]+){0,3})')";
        String[] fields = new String[4];
        for (int i=1;i<=4;i++) {
            fields[i-1] = "COALESCE(NULLIF(split_part(" + numeric + ",'.'," + i + "),'')::numeric, -1) DESC";
        }
        return String.join(", ", fields);
    }

    private static final RowMapper<RecordSummary> RECORD_MAPPER = (rs, rowNum) -> new RecordSummary(
            rs.getLong("id"), rs.getLong("source_id"), rs.getString("source_label"),
            rs.getString("title"), rs.getString("url"), rs.getString("tag_name"),
            rs.getString("published_at"), rs.getInt("current_version"),
            instant(rs, "first_seen_at"), instant(rs, "last_seen_at"),
            instant(rs, "latest_changed_at")
    );

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
