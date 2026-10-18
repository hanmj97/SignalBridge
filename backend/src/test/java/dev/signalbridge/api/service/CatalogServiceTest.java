package dev.signalbridge.api.service;

import dev.signalbridge.api.dto.ApiPage;
import dev.signalbridge.api.repository.CatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    @Mock CatalogRepository repository;
    @InjectMocks CatalogService service;

    @Test
    void rejectsInvalidPaginationBeforeQueryingDatabase() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.records(-1, 20, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsOversizedSearchInput() {
        assertThrows(ResponseStatusException.class,
                () -> service.records(0, 20, "a".repeat(101), null));
        verifyNoInteractions(repository);
    }

    @Test
    void missingRecordReturnsNotFound() {
        when(repository.findRecord(999L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.record(999L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void rejectsInvalidRecordHistoryId() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.recordHistory(0L));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void missingHistoryRecordReturnsNotFound() {
        when(repository.findRecord(999L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.recordHistory(999L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(repository).findRecord(999L);
    }

    @Test
    void validSearchIsTrimmedAndForwarded() {
        when(repository.findRecords(0, 5, "Grafana", 2L))
                .thenReturn(ApiPage.of(List.of(), 0, 5, 0));
        service.records(0, 5, "  Grafana  ", 2L);
        verify(repository).findRecords(0, 5, "Grafana", 2L);
    }
}
