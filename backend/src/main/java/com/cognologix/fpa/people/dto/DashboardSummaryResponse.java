package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.PeriodStatus;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
        int periodMonth,
        int periodYear,
        int versionNumber,
        PeriodStatus status,
        HeadcountSummary headcount,
        SalaryMetrics salaryMetrics,
        List<PuBreakdown> puBreakdown,
        List<ClientBreakdown> clientBreakdown,
        List<InternalBuBreakdown> internalBuBreakdown,
        ReconciliationSummary reconciliationSummary,
        DataQualitySummary dataQualitySummary
) {
    public record HeadcountSummary(
            int total,
            int billable,
            int bench,
            int support,
            int leadership,
            int management,
            BigDecimal billableRatioPct
    ) {}

    public record SalaryMetrics(
            BigDecimal totalGrossPay,
            BigDecimal billableGrossPay,
            BigDecimal benchGrossPay,
            BigDecimal supportGrossPay,
            BigDecimal leadershipGrossPay,
            BigDecimal managementGrossPay,
            BigDecimal avgPerHeadBillable,
            BigDecimal avgPerHeadBench,
            BigDecimal avgPerHeadSupport,
            BigDecimal avgPerHeadLeadership,
            BigDecimal avgPerHeadManagement
    ) {}

    public record PuBreakdown(
            String practiceUnit,
            int totalHc,
            int billableHc,
            int benchHc,
            BigDecimal billablePct,
            BigDecimal benchPct,
            BigDecimal totalGrossPay,
            BigDecimal billableGrossPay,
            BigDecimal benchGrossPay
    ) {}

    public record ClientBreakdown(
            String businessUnit,
            String customerCode,
            boolean isInternal,
            int totalHc,
            int billableHc,
            int nonBillableHc,
            BigDecimal billabilityPct,
            BigDecimal totalGrossPay
    ) {}

    public record InternalBuBreakdown(
            String businessUnit,
            String customerCode,
            int totalHc,
            int billableHc,
            int nonBillableHc,
            BigDecimal totalGrossPay,
            BigDecimal grossPayPct
    ) {}

    public record ReconciliationSummary(
            long payrollPending,
            long autoMatchedExited,
            long unmatched,
            long manuallyMapped
    ) {}

    public record DataQualitySummary(
            int totalWarnings,
            int missingProjectCode,
            int projectCodeNotFound,
            int billingClientUnresolved
    ) {}
}
