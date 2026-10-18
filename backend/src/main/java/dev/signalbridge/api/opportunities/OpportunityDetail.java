package dev.signalbridge.api.opportunities;

import java.util.List;

public record OpportunityDetail(OpportunityItem item, List<OpportunityChange> history) {}
