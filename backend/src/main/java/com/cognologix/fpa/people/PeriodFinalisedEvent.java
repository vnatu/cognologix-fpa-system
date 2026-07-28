package com.cognologix.fpa.people;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Published when a period version is finalised (ADR-018 / ADR-022 / ADR-045).
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
        BigDecimal billableEmployerContributions,
        BigDecimal benchEmployerContributions,
        BigDecimal supportEmployerContributions,
        BigDecimal leadershipEmployerContributions,
        BigDecimal managementEmployerContributions,
        BigDecimal billableTotalPayrollCost,
        BigDecimal benchTotalPayrollCost,
        BigDecimal supportTotalPayrollCost,
        BigDecimal leadershipTotalPayrollCost,
        BigDecimal managementTotalPayrollCost,
        List<BuPeriodActual> buActuals
) {
    public record BuPeriodActual(
            String businessUnit,
            int billableHc,
            BigDecimal totalGrossPay,
            BigDecimal totalEmployerContributions,
            BigDecimal totalPayrollCost
    ) {}

    public BigDecimal totalGrossPay() {
        return nullSafe(billableGrossPay)
                .add(nullSafe(benchGrossPay))
                .add(nullSafe(supportGrossPay))
                .add(nullSafe(leadershipGrossPay))
                .add(nullSafe(managementGrossPay));
    }

    public BigDecimal totalEmployerContributions() {
        return nullSafe(billableEmployerContributions)
                .add(nullSafe(benchEmployerContributions))
                .add(nullSafe(supportEmployerContributions))
                .add(nullSafe(leadershipEmployerContributions))
                .add(nullSafe(managementEmployerContributions));
    }

    public BigDecimal totalPayrollCost() {
        return nullSafe(billableTotalPayrollCost)
                .add(nullSafe(benchTotalPayrollCost))
                .add(nullSafe(supportTotalPayrollCost))
                .add(nullSafe(leadershipTotalPayrollCost))
                .add(nullSafe(managementTotalPayrollCost));
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
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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
