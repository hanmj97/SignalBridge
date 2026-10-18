package dev.signalbridge.api.dto;

import java.time.Instant;

public record SourceItem(
        long id,
        String sourceKey,
        String label,
        String provider,
        String resource,
        Instant lastCheckedAt,
        long recordCount
) {}
