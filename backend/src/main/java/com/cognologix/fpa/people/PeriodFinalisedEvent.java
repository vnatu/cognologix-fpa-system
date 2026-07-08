package com.cognologix.fpa.people;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a period version is finalised (ADR-018).
 * Budgeting &amp; Forecasting will consume this event to aggregate plan vs actual.
 */
public record PeriodFinalisedEvent(
        UUID periodVersionId,
        int billableHeadcount,
        int benchHeadcount,
        int supportHeadcount,
        int leadershipHeadcount,
        int managementHeadcount,
        BigDecimal totalGrossPay,
        Map<String, Integer> headcountByBusinessUnit
) {
    public static PeriodFinalisedEvent empty(UUID periodVersionId) {
        return new PeriodFinalisedEvent(
                periodVersionId,
                0, 0, 0, 0, 0,
                BigDecimal.ZERO,
                Map.of());
    }
}
