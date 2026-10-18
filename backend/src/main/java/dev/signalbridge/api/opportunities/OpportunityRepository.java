package dev.signalbridge.api.opportunities;

import dev.signalbridge.api.dto.ApiPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class OpportunityRepository {
    private final NamedParameterJdbcTemplate jdbc;

    // Deliberately separate from the existing member/Discord/legacy data source.
    // No extra DataSource bean is registered, preserving Spring Boot's primary pool.
    public OpportunityRepository(
            @Value("${SIGNALBRIDGE_V2_JDBC_URL:}") String v2Url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        if (v2Url == null || v2Url.isBlank()) {
            this.jdbc = null;
        } else {
            if (!v2Url.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("SIGNALBRIDGE_V2_JDBC_URL must use PostgreSQL JDBC");
            }
            DriverManagerDataSource source = new DriverManagerDataSource(v2Url, username, password);
            this.jdbc = new NamedParameterJdbcTemplate(source);
        }
    }

    private NamedParameterJdbcTemplate db() {
        if (jdbc == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "V2 opportunity database is not configured");
        return jdbc;
    }

    private static final String FROM = " FROM opportunity_details d " +
            "JOIN records r ON r.id=d.record_id JOIN sources s ON s.id=r.source_id ";
    // Product scope: youth-facing official policy + employment notices from allowed providers.
    // Adding an API provider must be an explicit, reviewed code change. Synthetic data
    // is allowed only from the clearly labelled V2 demo source (and is_demo remains visible).
    // In particular, generic business-grant sources are NOT in this list.
    private static final String CATALOG_SCOPE_GUARD = " AND (" +
            "(d.kind IN ('POLICY','BENEFIT','PROGRAM') " +
            "AND s.source_key IN ('v2_demo_opportunities','youthcenter_policies_official')) " +
            "OR (d.kind='JOB' AND s.source_key IN (" +
            "'v2_demo_opportunities'," +
            "'saramin_jobs_official','jobkorea_jobs_official'," +
            "'wanted_jobs_official','jasoseol_jobs_official'))" +
            // Only confirmed recruitment notices from the approved Yongin public API.
            // Exclude exams / results stored by earlier development collectors.
            " OR (d.kind='JOB' AND s.source_key='yongin_jobs_official' " +
            "AND d.category='채용정보' AND d.link_kind='DATASET' AND d.is_demo=FALSE)" +
            ") ";

    private static final String COLUMNS = "SELECT r.id, r.title, r.url, s.label AS source_label, " +
            "d.kind, d.organization, d.category, d.region_codes, d.application_start, d.link_kind, " +
            "d.application_deadline, d.status, d.age_min, d.age_max, d.employment_type, " +
            "d.eligibility_summary, d.benefit_summary, d.source_published_at, " +
            "r.first_seen_at, r.last_seen_at, r.current_version, d.is_demo ";

    private static Instant instant(ResultSet rs, String field) throws SQLException {
        Timestamp t = rs.getTimestamp(field);
        return t == null ? null : t.toInstant();
    }
    private static LocalDate date(ResultSet rs, String field) throws SQLException {
        java.sql.Date d = rs.getDate(field);
        return d == null ? null : d.toLocalDate();
    }
    private static Integer integer(ResultSet rs, String field) throws SQLException {
        Object value=rs.getObject(field);
        return value == null ? null : ((Number) value).intValue();
    }
    private static List<String> textArray(ResultSet rs, String field) throws SQLException {
        Array a = rs.getArray(field);
        if (a == null) return List.of();
        try { return Arrays.asList((String[])a.getArray()); }
        finally { a.free(); }
    }
    private static OpportunityItem map(ResultSet rs, int row) throws SQLException {
        return new OpportunityItem(rs.getLong("id"), rs.getString("title"), rs.getString("url"),
                rs.getString("source_label"), rs.getString("link_kind"), rs.getString("kind"), rs.getString("organization"),
                rs.getString("category"), textArray(rs,"region_codes"), date(rs,"application_start"),
                date(rs,"application_deadline"), rs.getString("status"), integer(rs,"age_min"),
                integer(rs,"age_max"), rs.getString("employment_type"), rs.getString("eligibility_summary"),
                rs.getString("benefit_summary"), instant(rs,"source_published_at"),
                instant(rs,"first_seen_at"), instant(rs,"last_seen_at"),
                rs.getInt("current_version"), rs.getBoolean("is_demo"));
    }

    // Keep the V2-2/3 method signature for compatibility with existing clients/tests.
    public ApiPage<OpportunityItem> list(int page, int size, String q, String kind,
                                          String region, boolean includeDemo) {
        return list(page, size, q, kind, region, includeDemo, null);
    }

    public ApiPage<OpportunityItem> list(int page, int size, String q, String kind,
                                          String region, boolean includeDemo, String scope) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid pagination");
        }
        if (q != null && q.length() > 100 || region != null && region.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filter too long");
        }
        if (kind != null && !kind.isBlank() && !List.of("POLICY", "BENEFIT", "JOB", "PROGRAM").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid kind");
        }
        if (scope != null && !scope.isBlank() && !List.of("YOUTH", "JOB").contains(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
        }
        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ").append(CATALOG_SCOPE_GUARD);
        // Separate public tabs without confusing youth employment support with job vacancies.
        if ("YOUTH".equals(scope)) where.append(" AND d.kind IN ('POLICY','BENEFIT','PROGRAM') ");
        if ("JOB".equals(scope)) where.append(" AND d.kind='JOB' ");
        if (!includeDemo) where.append(" AND d.is_demo=FALSE ");
        if (q != null && !q.isBlank()) {
            // Literal search; '%' and '_' cannot become LIKE wildcards.
            String escaped=q.trim().replace("!","!!").replace("%","!%").replace("_","!_");
            args.addValue("q","%"+escaped+"%");
            where.append(" AND (r.title ILIKE :q ESCAPE '!' OR d.organization ILIKE :q ESCAPE '!' " +
                    "OR d.category ILIKE :q ESCAPE '!') ");
        }
        if (kind != null && !kind.isBlank()) { args.addValue("kind",kind); where.append(" AND d.kind=:kind "); }
        if (region != null && !region.isBlank()) {
            args.addValue("region",region.trim());
            where.append(" AND CAST(:region AS text) = ANY(d.region_codes) ");
        }
        Long total = db().queryForObject("SELECT COUNT(*)"+FROM+where, args, Long.class);
        args.addValue("limit",size); args.addValue("offset",(long)page*size);
        List<OpportunityItem> items = db().query(COLUMNS+FROM+where+
                " ORDER BY r.first_seen_at DESC, r.id DESC LIMIT :limit OFFSET :offset",args,OpportunityRepository::map);
        return ApiPage.of(items,page,size,total==null?0:total);
    }

    public Optional<OpportunityDetail> detail(long id, boolean includeDemo) {
        String demoClause = (includeDemo ? "" : " AND d.is_demo=FALSE ") + CATALOG_SCOPE_GUARD;
        MapSqlParameterSource p = new MapSqlParameterSource("id",id);
        List<OpportunityItem> items = db().query(COLUMNS+FROM+" WHERE r.id=:id "+demoClause,p,OpportunityRepository::map);
        if (items.isEmpty()) return Optional.empty();
        List<OpportunityChange> changes = db().query("""
                SELECT version, change_type, changed_fields, detected_at
                FROM record_versions WHERE record_id=:id
                ORDER BY version DESC LIMIT 20
                """, p, (rs,row)->new OpportunityChange(rs.getInt("version"),rs.getString("change_type"),
                    textArray(rs,"changed_fields"), instant(rs,"detected_at")));
        return Optional.of(new OpportunityDetail(items.get(0),changes));
    }
}
