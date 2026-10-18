package dev.signalbridge.api.opportunities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Public projection. Unknown source fields stay null, demo flag is explicit. */
public record OpportunityItem(
    long id, String title, String url, String sourceLabel, String linkKind,
    String kind, String organization, String category, List<String> regionCodes,
    LocalDate applicationStart, LocalDate applicationDeadline, String status,
    Integer ageMin, Integer ageMax, String employmentType,
    String eligibilitySummary, String benefitSummary,
    Instant sourcePublishedAt, Instant firstSeenAt, Instant lastSeenAt,
    int currentVersion, @com.fasterxml.jackson.annotation.JsonProperty("isDemo") boolean isDemo
) {}
