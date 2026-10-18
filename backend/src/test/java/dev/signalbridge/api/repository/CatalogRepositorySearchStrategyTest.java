package dev.signalbridge.api.repository;

import dev.signalbridge.api.dto.RecordSummary;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CatalogRepositorySearchStrategyTest {

    private NamedParameterJdbcTemplate emptyJdbc() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        return jdbc;
    }

    @Test
    void originalOrIsUnchangedByDefault() {
        NamedParameterJdbcTemplate jdbc = emptyJdbc();
        CatalogRepository repository = new CatalogRepository(jdbc, "OR");
        repository.findRecords(0, 10, "Grafana", null);

        ArgumentCaptor<String> count = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(count.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertTrue(count.getValue().contains("r.title ILIKE :query ESCAPE '!' OR s.label ILIKE :query ESCAPE '!'"));
        assertFalse(count.getValue().contains("UNION"));
    }

    @Test
    void unionSearchHasTwoBranchesAndDeduplicatesIds() {
        NamedParameterJdbcTemplate jdbc = emptyJdbc();
        CatalogRepository repository = new CatalogRepository(jdbc, "UNION");
        repository.findRecords(2, 20, "Grafana", 3L);

        ArgumentCaptor<String> count = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(count.capture(), any(SqlParameterSource.class), eq(Long.class));
        String sql = count.getValue();
        assertTrue(sql.contains("UNION"));
        assertFalse(sql.contains("UNION ALL"));
        assertEquals(2, sql.split("r.source_id = :sourceId", -1).length - 1);
        assertTrue(sql.contains("r.title ILIKE :query ESCAPE '!'"));
        assertTrue(sql.contains("s.label ILIKE :query ESCAPE '!'"));
    }

    @Test
    void noSearchAlwaysUsesExistingPathEvenWhenUnionEnabled() {
        NamedParameterJdbcTemplate jdbc = emptyJdbc();
        CatalogRepository repository = new CatalogRepository(jdbc, "UNION");
        repository.findRecords(0, 10, "", 3L);

        ArgumentCaptor<String> count = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(count.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertFalse(count.getValue().contains("UNION"));
        assertTrue(count.getValue().contains("r.source_id = :sourceId"));
    }

    @Test
    void wildcardCharactersAreEscapedLiterally() {
        NamedParameterJdbcTemplate jdbc = emptyJdbc();
        CatalogRepository repository = new CatalogRepository(jdbc, "UNION");
        repository.findRecords(0, 10, "%_!", null);

        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(anyString(), parameters.capture(), eq(Long.class));
        assertEquals("%!%!_!!%", parameters.getValue().getValue("query"));
    }

    @Test
    void invalidStrategyFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogRepository(mock(NamedParameterJdbcTemplate.class), "AUTO"));
    }
}
