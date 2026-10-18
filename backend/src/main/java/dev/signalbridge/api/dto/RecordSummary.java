package dev.signalbridge.api.dto;

import java.time.Instant;

public record RecordSummary(
        long id,
        long sourceId,
        String sourceLabel,
        String title,
        String url,
        String tagName,
        String publishedAt,
        int currentVersion,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant latestChangedAt
) {}
