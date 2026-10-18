package dev.signalbridge.api.dto;

import java.time.Instant;
import java.util.List;

public record ChangeItem(
        long id,
        long recordId,
        String recordTitle,
        String sourceLabel,
        int version,
        String changeType,
        List<String> changedFields,
        Instant detectedAt
) {}
