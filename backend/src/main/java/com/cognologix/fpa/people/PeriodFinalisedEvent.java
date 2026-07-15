package com.cognologix.fpa.people;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Published when a period version is finalised (ADR-018 / ADR-022).
 * Budgeting &amp; Forecasting consumes this event to snapshot HC and salary actuals.
 */
public record PeriodFinalisedEvent(
        UUID periodVersionId,
        int periodMonth,
        int periodYear,
        int billableHeadcount,
        int benchHeadcount,
        int supportHeadcount,
        int leadershipHeadcount,
        int managementHeadcount,
        BigDecimal billableGrossPay,
        BigDecimal benchGrossPay,
        BigDecimal supportGrossPay,
        BigDecimal leadershipGrossPay,
        BigDecimal managementGrossPay,
        List<BuPeriodActual> buActuals
) {
    public record BuPeriodActual(String businessUnit, int billableHc, BigDecimal totalGrossPay) {}

    public BigDecimal totalGrossPay() {
        return nullSafe(billableGrossPay)
                .add(nullSafe(benchGrossPay))
                .add(nullSafe(supportGrossPay))
                .add(nullSafe(leadershipGrossPay))
                .add(nullSafe(managementGrossPay));
    }

    public int totalHeadcount() {
        return billableHeadcount + benchHeadcount + supportHeadcount
                + leadershipHeadcount + managementHeadcount;
    }

    /** Convenience map of business unit → billable HC (legacy consumers). */
    public Map<String, Integer> headcountByBusinessUnit() {
        if (buActuals == null || buActuals.isEmpty()) {
            return Map.of();
        }
        return buActuals.stream()
                .collect(Collectors.toMap(BuPeriodActual::businessUnit, BuPeriodActual::billableHc, Integer::sum));
    }

    public static PeriodFinalisedEvent empty(UUID periodVersionId, int periodMonth, int periodYear) {
        return new PeriodFinalisedEvent(
                periodVersionId,
                periodMonth,
                periodYear,
                0, 0, 0, 0, 0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of());
    }

    /** @deprecated Prefer {@link #empty(UUID, int, int)} with period month/year. */
    public static PeriodFinalisedEvent empty(UUID periodVersionId) {
        return empty(periodVersionId, 1, 1970);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
