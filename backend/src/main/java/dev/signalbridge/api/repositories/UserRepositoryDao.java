package dev.signalbridge.api.repositories;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepositoryDao {
    private final NamedParameterJdbcTemplate jdbc;
    public UserRepositoryDao(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    public record Entry(long sourceId, String label, String resource, String sourceKey, String lastCheckedAt, long recordCount) {}

    public List<Entry> list(long userId) {
        return jdbc.query("""
                SELECT s.id, s.label, s.resource, s.source_key, s.last_checked_at,
                       (SELECT COUNT(*) FROM records r WHERE r.source_id=s.id) AS record_count
                FROM user_repository_subscriptions u
                JOIN sources s ON s.id=u.source_id
                WHERE u.user_id=:uid ORDER BY u.created_at DESC, s.id DESC
                """, new MapSqlParameterSource("uid",userId), (rs,n)->new Entry(
                rs.getLong("id"),rs.getString("label"),rs.getString("resource"),rs.getString("source_key"),
                rs.getTimestamp("last_checked_at")==null?null:rs.getTimestamp("last_checked_at").toInstant().toString(),
                rs.getLong("record_count")));
    }
    public int count(long userId) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM user_repository_subscriptions WHERE user_id=:uid",
                new MapSqlParameterSource("uid",userId),Integer.class);
        return n==null?0:n;
    }
    @Transactional
    public void add(long userId, String repo) {
        // Prefer existing built-in sources. Avoid duplicating network collection for the same repo.
        var repoParams=new MapSqlParameterSource("repo",repo);
        List<Long> existing=jdbc.query("SELECT id FROM sources WHERE provider='github_releases' AND lower(resource)=lower(:repo) ORDER BY id LIMIT 1",
                repoParams,(rs,n)->rs.getLong(1));
        long sourceId;
        if (!existing.isEmpty()) sourceId=existing.get(0);
        else {
            String key="github_user_"+sha(repo.toLowerCase(java.util.Locale.ROOT));
            var params=new MapSqlParameterSource("k",key).addValue("repo",repo);
            jdbc.update("""
                    INSERT INTO sources (source_key, label, provider, resource, enabled)
                    VALUES (:k, :repo, 'github_releases', :repo, TRUE)
                    ON CONFLICT (source_key) DO NOTHING
                    """,params);
            sourceId=jdbc.queryForObject("SELECT id FROM sources WHERE source_key=:k AND lower(resource)=lower(:repo)",params,Long.class);
        }
        jdbc.update("""
                INSERT INTO user_repository_subscriptions (user_id,source_id)
                VALUES (:uid,:sid) ON CONFLICT (user_id,source_id) DO NOTHING
                """,new MapSqlParameterSource("uid",userId).addValue("sid",sourceId));
    }
    public boolean delete(long userId,long sourceId) {
        return jdbc.update("DELETE FROM user_repository_subscriptions WHERE user_id=:uid AND source_id=:sid",
                new MapSqlParameterSource("uid",userId).addValue("sid",sourceId))>0;
    }
    private static String sha(String value){
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,24);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
