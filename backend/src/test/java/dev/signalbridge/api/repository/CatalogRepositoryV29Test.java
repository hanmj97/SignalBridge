package dev.signalbridge.api.repository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;
import java.util.List;
class CatalogRepositoryV29Test {
    private NamedParameterJdbcTemplate fake(){
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(),any(SqlParameterSource.class),any(RowMapper.class))).thenReturn(List.of());
        return jdbc;
    }
    @Test void fixesV28WhitespaceAndSortsByPublicationDate(){
        for(String strategy:List.of("OR","UNION")){
            var db=fake();new CatalogRepository(db,strategy).findRecords(0,10,"Grafana",null);
            var q=ArgumentCaptor.forClass(String.class);
            verify(db).query(q.capture(),any(SqlParameterSource.class),any(RowMapper.class));
            String sql = q.getValue();
            // Both strategies require the lateral join, but UNION has an
            // additional JOIN (matched IDs) before that lateral join.
            assertTrue(sql.contains(" LEFT JOIN LATERAL"), sql);
            if ("OR".equals(strategy)) {
                assertTrue(sql.contains("r.source_id LEFT JOIN LATERAL"), sql);
            } else {
                assertTrue(sql.contains("r.source_id JOIN ("), sql);
                assertTrue(sql.contains("matched.id = r.id LEFT JOIN LATERAL"), sql);
            }
            assertTrue(sql.contains("published_at"), sql);
            assertTrue(sql.contains("split_part"), sql);
            assertFalse(sql.contains("source_idLEFT JOIN"), sql);
        }
    }
    @Test void memberRepoFilterEnforcedInQueryAndCount(){
        var db=fake();new CatalogRepository(db,"OR").findMyRecords(0,10,"",null,27L);
        var q=ArgumentCaptor.forClass(String.class);
        verify(db).query(q.capture(),any(SqlParameterSource.class),any(RowMapper.class));
        assertTrue(q.getValue().contains("urs.user_id=:userId"));
        verify(db).queryForObject(contains("urs.user_id=:userId"),any(SqlParameterSource.class),eq(Long.class));
    }
    @Test void versionListUsesPublicationNotBulkImportTimestamp(){
        var db=fake();new CatalogRepository(db,"OR").findChanges(0,10,null);
        var q=ArgumentCaptor.forClass(String.class);
        verify(db).query(q.capture(),any(SqlParameterSource.class),any(RowMapper.class));
        assertTrue(q.getValue().contains("v.payload->>'published_at'"));
    }
}
