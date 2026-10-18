package dev.signalbridge.api.watches;

import java.time.Instant;

public final class WatchDtos {
    private WatchDtos() {}
    public record WatchRule(long id, String keyword, Long sourceId, String sourceLabel,
                            boolean enabled, Instant createdAt) {}
    public record CreateWatch(String keyword, Long sourceId) {}
    public record SetEnabled(Boolean enabled) {}
    public record WatchEvent(long id, long ruleId, String keyword, long recordId, long versionId,
                             String title, String sourceLabel, String url, String changeType,
                             String status, Instant detectedAt, Instant deliveredAt) {}
}
