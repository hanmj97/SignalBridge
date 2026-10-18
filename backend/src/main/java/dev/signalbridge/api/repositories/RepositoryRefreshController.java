package dev.signalbridge.api.repositories;

import dev.signalbridge.api.auth.AccountPrincipal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated, queue-only GitHub refresh requests.
 * No process execution, arbitrary URL fetch, or Airflow credentials on the web server.
 */
@RestController
@RequestMapping("/api/v1/my-repositories")
public class RepositoryRefreshController {
    private final NamedParameterJdbcTemplate jdbc;

    public record RefreshItem(long id, long sourceId, String status,
                              Instant requestedAt, Instant startedAt, Instant finishedAt) {}

    public RepositoryRefreshController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private MapSqlParameterSource params(long userId, long sourceId) {
        return new MapSqlParameterSource("uid", userId).addValue("sid", sourceId);
    }

    private void requireSubscription(MapSqlParameterSource p) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_repository_subscriptions u
                JOIN sources s ON s.id = u.source_id
                WHERE u.user_id = :uid AND u.source_id = :sid
                  AND s.enabled = TRUE AND s.provider = 'github_releases'
                """, p, Integer.class);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "등록된 공개 GitHub 저장소가 아닙니다.");
        }
    }

    private static Instant date(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private List<RefreshItem> latest(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT id, source_id, status, requested_at, started_at, finished_at
                FROM github_refresh_requests
                WHERE user_id = :uid AND source_id = :sid
                ORDER BY requested_at DESC, id DESC LIMIT 1
                """, p, (rs, index) -> new RefreshItem(
                rs.getLong("id"), rs.getLong("source_id"), rs.getString("status"),
                date(rs.getTimestamp("requested_at")), date(rs.getTimestamp("started_at")),
                date(rs.getTimestamp("finished_at"))));
    }

    @GetMapping("/{sourceId}/refresh")
    public RefreshItem status(@AuthenticationPrincipal AccountPrincipal member,
                              @PathVariable long sourceId) {
        var p = params(member.id(), sourceId);
        requireSubscription(p);
        var entries = latest(p);
        return entries.isEmpty() ? null : entries.get(0);
    }

    @PostMapping("/{sourceId}/refresh")
    @Transactional
    public ResponseEntity<RefreshItem> enqueue(@AuthenticationPrincipal AccountPrincipal member,
                                                @PathVariable long sourceId) {
        var p = params(member.id(), sourceId);
        requireSubscription(p);
        // 2-minute per-member/source cooldown and a unique unfinished-request index.
        // A concurrent duplicate returns the existing request, not a second API call.
        jdbc.query("""
                INSERT INTO github_refresh_requests (user_id, source_id, status)
                SELECT :uid, :sid, 'PENDING'
                WHERE NOT EXISTS (
                    SELECT 1 FROM github_refresh_requests
                    WHERE user_id = :uid AND source_id = :sid
                      AND requested_at > now() - INTERVAL '2 minutes'
                )
                ON CONFLICT DO NOTHING
                RETURNING id
                """, p, (rs, index) -> rs.getLong("id"));
        var entries = latest(p);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "요청을 등록하지 못했습니다. 잠시 후 재시도해 주세요.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(entries.get(0));
    }
}
