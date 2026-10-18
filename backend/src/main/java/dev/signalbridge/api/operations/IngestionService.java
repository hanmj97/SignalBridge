package dev.signalbridge.api.operations;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class IngestionService {
    private final IngestionRepository repository;
    private final Clock clock;

    @Autowired
    public IngestionService(IngestionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    // Clock can be fixed for deterministic unit tests.
    IngestionService(IngestionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public IngestionSnapshot snapshot() {
        List<IngestionSnapshot.SourceIngestion> sources = repository.findSourcesWithLatestRun();
        long[] counts = repository.countLast24Hours();
        List<IngestionSnapshot.RecentIngestion> recent = repository.findRecentRuns();
        return new IngestionSnapshot(Instant.now(clock), sources.size(),
                counts[0], counts[1], counts[2], sources, recent);
    }
}
