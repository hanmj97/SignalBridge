package dev.signalbridge.api.opportunities;

import java.time.Instant;
import java.util.List;

public record OpportunityChange(int version, String changeType, List<String> changedFields, Instant detectedAt) {}
