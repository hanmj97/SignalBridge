package dev.signalbridge.api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.mockito.ArgumentCaptor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogRepositoryNewestChangeTest {
    private NamedParameterJdbcTemplate mockJdbc() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(0L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        return jdbc;
    }

    @Test
    void recordsUseMostRecentVersionNotLastPollOrInitialDiscovery() {
        for (String strategy : List.of("OR", "UNION")) {
            NamedParameterJdbcTemplate jdbc = mockJdbc();
            CatalogRepository repository = new CatalogRepository(jdbc, strategy);
            repository.findRecords(0, 10, "Docker", null);
            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
            String query = sql.getValue();
            assertTrue(query.contains("LEFT JOIN LATERAL"));
            assertTrue(query.contains("ORDER BY v.version DESC LIMIT 1"));
            assertTrue(query.contains("latest_changed_at"));
            assertTrue(query.contains("published_at"));
            // UNION query inserts a JOIN of matched record IDs before LATERAL.
            // Check each valid join layout rather than assuming they are identical.
            if ("OR".equals(strategy)) {
                assertTrue(query.contains("r.source_id LEFT JOIN LATERAL"), query);
            } else {
                assertTrue(query.contains("r.source_id JOIN ("), query);
                assertTrue(query.contains("matched.id = r.id LEFT JOIN LATERAL"), query);
            }
            assertFalse(query.contains("source_idLEFT JOIN"), query);
            assertFalse(query.contains("ORDER BY r.last_seen_at DESC"), query);
        }
    }

    @Test
    void changesSortByActualDetectionTimestamp() {
        NamedParameterJdbcTemplate jdbc = mockJdbc();
        new CatalogRepository(jdbc, "OR").findChanges(0, 10, null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("v.payload->>'published_at'"));
    }
}
