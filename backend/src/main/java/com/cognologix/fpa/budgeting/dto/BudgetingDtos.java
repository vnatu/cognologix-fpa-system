package com.cognologix.fpa.budgeting.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Calculation / analysis response types for Budgeting &amp; Forecasting (ADR-037 / ADR-038). */
public final class BudgetingDtos {

    private BudgetingDtos() {}

    /** Rolling Forecast = actuals for finalised months + ACTIVE Normal plan for the rest. */
    public record RollingForecastResult(
            UUID financialYearPlanId,
            String fiscalYear,
            UUID baselineVersionId,
            List<MonthlyFinancials> months,
            /** ADR-049 — always 12 months; highlight fields describe the selected period. */
            String granularity,
            String periodLabel,
            Integer highlightMonth,
            Integer highlightYear,
            Integer highlightQuarter
    ) {}

    /**
     * Delta = Rolling Forecast − Baseline (ACTIVE Normal).
     *
     * <p>Sign convention (for frontend traffic-lights):
     * <ul>
     *   <li>Revenue / Gross Profit / EBITDA: positive = above plan (good), negative = below plan (bad)</li>
     *   <li>Costs (Salary, Overhead, COGS, OpEx): positive = over-budget (bad), negative = under-budget (good)</li>
     * </ul>
     */
    public record DeltaResult(
            UUID financialYearPlanId,
            String fiscalYear,
            UUID baselineVersionId,
            List<MonthlyFinancials> months,
            String granularity,
            String periodLabel,
            /** Aggregated delta for the selected period (ADR-049). */
            MonthlyFinancials periodTotal
    ) {}

    public record PlanVsActualResult(
            UUID financialYearPlanId,
            String fiscalYear,
            UUID baselineVersionId,
            List<MonthlyPlanVsActual> months,
            PeriodTotals q1,
            PeriodTotals q2,
            PeriodTotals q3,
            PeriodTotals q4,
            PeriodTotals fy,
            String granularity,
            String periodLabel,
            /** Period-scoped totals for Headline KPIs / summary cards (ADR-049). */
            PeriodTotals selectedPeriod,
            /** e.g. {@code Actuals: Apr–Jun 2026 (3 of 12 months)} — set for ANNUAL when partial. */
            String actualsCoverageNote,
            Integer monthsWithActuals,
            Integer totalMonthsInFy
    ) {}

    public record CostPerEmployeeResult(
            UUID financialYearPlanId,
            Integer month,
            Integer year,
            Integer quarter,
            String granularity,
            String periodLabel,
            boolean fromActuals,
            CategoryCost billable,
            CategoryCost bench,
            CategoryCost support,
            CategoryCost leadership,
            BigDecimal totalCostPerBillableHead
    ) {}

    public record BuMetricsResult(
            UUID financialYearPlanId,
            Integer month,
            Integer year,
            Integer quarter,
            String granularity,
            String periodLabel,
            List<BuMetricRow> rows
    ) {}

    /** BU Analysis — dedicated sub-section under Budgeting &amp; Forecasting (ADR-051). */
    public record BuAnalysisResult(
            UUID financialYearPlanId,
            Integer month,
            Integer year,
            Integer quarter,
            String granularity,
            String periodLabel,
            BigDecimal totalCompanyPayrollCost,
            BigDecimal totalCompanyRevenue,
            int totalCompanyHc,
            List<ExternalBuAnalysisRow> externalBUs,
            List<InternalBuAnalysisRow> internalBUs
    ) {}

    public record ExternalBuAnalysisRow(
            String customerCode,
            String customerName,
            int totalHc,
            int billableHc,
            int nonBillableHc,
            BigDecimal totalGrossPay,
            BigDecimal billableGrossPay,
            BigDecimal nonBillableGrossPay,
            BigDecimal totalPayrollCost,
            BigDecimal billablePayrollCost,
            BigDecimal nonBillablePayrollCost,
            BigDecimal avgPayrollCostPerHead,
            BigDecimal buCostPctOfTotal,
            BigDecimal buRevenuePctOfTotal,
            BigDecimal actualRevenue,
            BigDecimal grossMargin,
            BigDecimal grossMarginPct,
            List<PositionBreakdownRow> positionBreakdown
    ) {}

    public record InternalBuAnalysisRow(
            String customerCode,
            String customerName,
            int totalHc,
            int billableHc,
            int nonBillableHc,
            BigDecimal totalGrossPay,
            BigDecimal totalPayrollCost,
            BigDecimal avgPayrollCostPerHead,
            BigDecimal buCostPctOfTotal,
            List<PositionBreakdownRow> positionBreakdown
    ) {}

    public record PositionBreakdownRow(
            String title,
            int headcount,
            BigDecimal avgPayrollCost,
            BigDecimal pctOfBuHc
    ) {}

    public record MonthlyFinancials(
            int month,
            int year,
            boolean fromActuals,
            HcFigures hc,
            SalaryFigures salary,
            List<ClientRevenueFigures> revenueByClient,
            BigDecimal totalRevenue,
            List<OverheadLineFigures> overhead,
            BigDecimal totalOverhead,
            BigDecimal totalSalaryCost,
            BigDecimal statutoryBenefits,
            BigDecimal variablePay,
            BigDecimal totalCogs,
            BigDecimal grossProfit,
            BigDecimal totalOpex,
            BigDecimal ebitda
    ) {}

    public record MonthlyPlanVsActual(
            int month,
            int year,
            boolean hasActuals,
            /** {@code REVENUE_MODULE}, {@code MANUAL_OVERRIDE}, or null when no revenue actuals. */
            String revenueSource,
            TriadHc hc,
            TriadSalary salary,
            List<TriadClientRevenue> revenueByClient,
            MoneyTriad totalRevenue,
            List<TriadOverhead> overhead,
            MoneyTriad totalOverhead,
            MoneyTriad totalSalaryCost,
            MoneyTriad statutoryBenefits,
            MoneyTriad totalCogs,
            MoneyTriad grossProfit,
            MoneyTriad ebitda
    ) {}

    public record PeriodTotals(
            String label,
            MoneyTriad totalRevenue,
            MoneyTriad totalSalaryCost,
            MoneyTriad totalOverhead,
            MoneyTriad totalCogs,
            MoneyTriad grossProfit,
            MoneyTriad ebitda
    ) {}

    public record HcFigures(
            int billableHc,
            int benchHc,
            int supportHc,
            int leadershipHc,
            int managementHc,
            int totalHc
    ) {}

    public record SalaryFigures(
            BigDecimal billable,
            BigDecimal bench,
            BigDecimal support,
            BigDecimal cofounders,
            BigDecimal seniorMgmt,
            BigDecimal total
    ) {}

    public record ClientRevenueFigures(
            UUID customerId,
            String customerCode,
            String customerName,
            BigDecimal tmRevenue,
            BigDecimal fixedBidRevenue,
            BigDecimal totalRevenue
    ) {}

    public record OverheadLineFigures(String lineCode, BigDecimal amount) {}

    public record CategoryCost(
            String category,
            int headcount,
            BigDecimal grossPayPerHead,
            BigDecimal employerContributionsPerHead,
            /** {@code ACTUAL} when from period_actuals; {@code ESTIMATE_13PCT} for plan months. */
            String employerContributionsSource,
            BigDecimal layer1,
            BigDecimal layer2,
            BigDecimal layer3,
            BigDecimal total
    ) {}

    public record BuMetricRow(
            UUID customerId,
            String customerCode,
            String customerName,
            boolean internal,
            BigDecimal plannedRevenue,
            BigDecimal actualRevenue,
            BigDecimal plannedSalaryCost,
            BigDecimal actualSalaryCost,
            Integer plannedBillableHc,
            Integer actualBillableHc,
            BigDecimal plannedGrossMargin,
            BigDecimal actualGrossMargin,
            BigDecimal plannedGrossMarginPct,
            BigDecimal actualGrossMarginPct,
            BigDecimal avgSalaryPerHead
    ) {}

    public record MoneyTriad(BigDecimal plan, BigDecimal actual, BigDecimal variance) {}

    public record TriadHc(HcFigures plan, HcFigures actual, HcFigures variance) {}

    public record TriadSalary(SalaryFigures plan, SalaryFigures actual, SalaryFigures variance) {}

    public record TriadClientRevenue(
            UUID customerId,
            String customerCode,
            MoneyTriad tmRevenue,
            MoneyTriad fixedBidRevenue,
            MoneyTriad totalRevenue
    ) {}

    public record TriadOverhead(String lineCode, MoneyTriad amount) {}
}
