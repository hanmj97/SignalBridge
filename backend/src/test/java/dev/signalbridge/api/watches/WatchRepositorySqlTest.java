package dev.signalbridge.api.watches;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression for Engineering V2-12 member repository watch validation.
 * This tests the SQL passed to JDBC, not only the service mock.
 */
class WatchRepositorySqlTest {
    @Test
    void memberSourceAuthorizationSqlHasBalancedParentheses() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(true);
        var repository = new WatchRepository(jdbc);
        assertTrue(repository.sourceExists(17L, 42L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), anyMap(), eq(Boolean.class));
        String statement = sql.getValue();
        long opens = statement.chars().filter(c -> c == '(').count();
        long closes = statement.chars().filter(c -> c == ')').count();

        assertEquals(opens, closes,
                "Authorization EXISTS SQL must not have an extra closing parenthesis");
        assertTrue(statement.contains("user_repository_subscriptions"));
        assertTrue(statement.contains("u.user_id=:uid"));
        assertTrue(statement.contains("u.source_id=s.id"));
        assertFalse(statement.contains("NOT starts_with"));
    }

    @Test
    void sourceNotSubscribedByMemberIsRejected() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(false);
        assertFalse(new WatchRepository(jdbc).sourceExists(17L, 42L));
    }
}
