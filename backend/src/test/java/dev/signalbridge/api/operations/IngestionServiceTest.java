package dev.signalbridge.api.operations;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionServiceTest {
    @Test
    void snapshotReportsRealCountsAndSourceWithoutLatestRun() {
        IngestionRepository repo = mock(IngestionRepository.class);
        var unknown = new IngestionSnapshot.SourceIngestion(
                7L, "Source A", "example/repo", null,
                null, null, null, null, 0, 0, 0);
        when(repo.findSourcesWithLatestRun()).thenReturn(List.of(unknown));
        when(repo.countLast24Hours()).thenReturn(new long[]{8, 6, 2});
        when(repo.findRecentRuns()).thenReturn(List.of());

        var now = Instant.parse("2026-10-18T00:00:00Z");
        var service = new IngestionService(repo, Clock.fixed(now, ZoneOffset.UTC));
        var snapshot = service.snapshot();

        assertEquals(now, snapshot.generatedAt());
        assertEquals(1, snapshot.activeSourceCount());
        assertEquals(8L, snapshot.runsLast24Hours());
        assertEquals(6L, snapshot.completedLast24Hours());
        assertEquals(2L, snapshot.failuresLast24Hours());
        assertNull(snapshot.sources().get(0).latestStatus());
        assertTrue(snapshot.recentRuns().isEmpty());
        verify(repo).findSourcesWithLatestRun();
        verify(repo).findRecentRuns();
    }

    @Test
    void snapshotDoesNotContainRawDatabaseErrorText() {
        // Response DTO exposes only whitelisted columns and never ingestion_runs.error_message.
        var members = java.util.Arrays.stream(IngestionSnapshot.RecentIngestion.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertFalse(members.contains("errorMessage"));
        assertFalse(members.contains("webhookUrl"));
    }
}
