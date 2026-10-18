package dev.signalbridge.api.repositories;

import dev.signalbridge.api.auth.AccountPrincipal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryRefreshControllerTest {
    private final AccountPrincipal member =
            new AccountPrincipal(27, "demo@example.org", "test", "unused", true);

    @Test
    void cannotQueueForSomeoneElsesRepository() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        var controller = new RepositoryRefreshController(jdbc);
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.enqueue(member, 99));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(jdbc, never()).query(contains("INSERT INTO github_refresh_requests"),
                any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void queuesOwnedRepositoryAndReturnsAcceptedStatus() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
        var status = new RepositoryRefreshController.RefreshItem(100, 7, "PENDING",
                Instant.now(), null, null);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("INSERT INTO github_refresh_requests")) return List.of(100L);
                    return List.of(status);
                });
        var controller = new RepositoryRefreshController(jdbc);
        var result = controller.enqueue(member, 7);
        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertEquals("PENDING", result.getBody().status());
        assertEquals(7, result.getBody().sourceId());
    }

    @Test
    void emptyHistoryDoesNotLeakAnotherUsersStatus() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        assertNull(new RepositoryRefreshController(jdbc).status(member, 7));
    }
}
