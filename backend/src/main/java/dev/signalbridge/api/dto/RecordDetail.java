package dev.signalbridge.api.dto;

import java.time.Instant;

public record RecordDetail(
        long id,
        long sourceId,
        String sourceLabel,
        String title,
        String url,
        String tagName,
        String publishedAt,
        String description,
        boolean prerelease,
        int currentVersion,
        Instant firstSeenAt,
        Instant lastSeenAt
) {}
