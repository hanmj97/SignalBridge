package dev.signalbridge.api.operations;

import java.time.Instant;
import java.util.List;

/** Observed ingestion data from SignalBridge PostgreSQL, NOT an Airflow DAG status. */
public record IngestionSnapshot(
        Instant generatedAt,
        int activeSourceCount,
        long runsLast24Hours,
        long completedLast24Hours,
        long failuresLast24Hours,
        List<SourceIngestion> sources,
        List<RecentIngestion> recentRuns
) {
    public record SourceIngestion(
            long sourceId,
            String label,
            String resource,
            Instant lastCheckedAt,
            Long latestRunId,
            String latestStatus,
            Instant latestStartedAt,
            Instant latestFinishedAt,
            int latestFetchedCount,
            int latestNewCount,
            int latestUpdatedCount
    ) {}

    public record RecentIngestion(
            long id,
            long sourceId,
            String sourceLabel,
            String status,
            Instant startedAt,
            Instant finishedAt,
            int fetchedCount,
            int newCount,
            int updatedCount
    ) {}
}
