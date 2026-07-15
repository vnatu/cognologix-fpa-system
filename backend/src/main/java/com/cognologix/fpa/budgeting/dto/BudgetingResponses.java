package com.cognologix.fpa.budgeting.dto;

import com.cognologix.fpa.budgeting.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Response DTOs for Budgeting REST API — no JPA entities leaked to clients. */
public final class BudgetingResponses {

    private BudgetingResponses() {}

    public record PlanSummaryResponse(
            UUID id,
            String fiscalYear,
            LocalDate fiscalYearStart,
            LocalDate fiscalYearEnd,
            int openingHc,
            Instant createdAt,
            String createdBy
    ) {
        public static PlanSummaryResponse from(FinancialYearPlan plan) {
            return new PlanSummaryResponse(
                    plan.getId(),
                    plan.getFiscalYear(),
                    plan.getFiscalYearStart(),
                    plan.getFiscalYearEnd(),
                    plan.getOpeningHc(),
                    plan.getCreatedAt(),
                    plan.getCreatedBy());
        }
    }

    public record PlanDetailResponse(
            UUID id,
            String fiscalYear,
            LocalDate fiscalYearStart,
            LocalDate fiscalYearEnd,
            int openingHc,
            Instant createdAt,
            String createdBy,
            List<ForecastTypeResponse> forecastTypes
    ) {
        public static PlanDetailResponse from(FinancialYearPlan plan) {
            List<ForecastTypeResponse> types = plan.getForecastTypes().stream()
                    .sorted(Comparator.comparing(ForecastType::getTypeName))
                    .map(ForecastTypeResponse::from)
                    .toList();
            return new PlanDetailResponse(
                    plan.getId(),
                    plan.getFiscalYear(),
                    plan.getFiscalYearStart(),
                    plan.getFiscalYearEnd(),
                    plan.getOpeningHc(),
                    plan.getCreatedAt(),
                    plan.getCreatedBy(),
                    types);
        }
    }

    public record ForecastTypeResponse(
            UUID id,
            String typeName,
            boolean primary,
            List<ForecastVersionResponse> versions
    ) {
        public static ForecastTypeResponse from(ForecastType type) {
            List<ForecastVersionResponse> versions = type.getVersions().stream()
                    .sorted(Comparator.comparingInt(ForecastVersion::getVersionNumber))
                    .map(ForecastVersionResponse::from)
                    .toList();
            return new ForecastTypeResponse(
                    type.getId(), type.getTypeName(), type.isPrimary(), versions);
        }
    }

    public record ForecastVersionResponse(
            UUID id,
            int versionNumber,
            String status,
            Instant publishedAt,
            String publishedBy,
            Instant supersededAt,
            String supersededBy,
            Instant createdAt,
            String createdBy
    ) {
        public static ForecastVersionResponse from(ForecastVersion version) {
            return new ForecastVersionResponse(
                    version.getId(),
                    version.getVersionNumber(),
                    version.getStatus().name(),
                    version.getPublishedAt(),
                    version.getPublishedBy(),
                    version.getSupersededAt(),
                    version.getSupersededBy(),
                    version.getCreatedAt(),
                    version.getCreatedBy());
        }
    }

    public record PeriodActualsResponse(
            UUID id,
            int actualsMonth,
            int actualsYear,
            Integer actualBillableHc,
            Integer actualBenchHc,
            Integer actualSupportHc,
            Integer actualLeadershipHc,
            Integer actualManagementHc,
            Integer actualTotalHc,
            BigDecimal actualBillableSalaries,
            BigDecimal actualBenchSalaries,
            BigDecimal actualSupportSalaries,
            BigDecimal actualLeadershipSalaries,
            BigDecimal actualManagementSalaries,
            BigDecimal actualRevenueManual,
            UUID peoplePeriodVersionId,
            Instant createdAt
    ) {
        public static PeriodActualsResponse from(PeriodActuals a) {
            return new PeriodActualsResponse(
                    a.getId(),
                    a.getActualsMonth(),
                    a.getActualsYear(),
                    a.getActualBillableHc(),
                    a.getActualBenchHc(),
                    a.getActualSupportHc(),
                    a.getActualLeadershipHc(),
                    a.getActualManagementHc(),
                    a.getActualTotalHc(),
                    a.getActualBillableSalaries(),
                    a.getActualBenchSalaries(),
                    a.getActualSupportSalaries(),
                    a.getActualLeadershipSalaries(),
                    a.getActualManagementSalaries(),
                    a.getActualRevenueManual(),
                    a.getPeoplePeriodVersionId(),
                    a.getCreatedAt());
        }
    }

    public record HcPlanMonthResponse(
            int planMonth,
            int planYear,
            int plannedHires,
            int plannedExits,
            int plannedBillableHc,
            int plannedBenchHc,
            int plannedSupportHc,
            int plannedLeadershipHc,
            int plannedManagementHc
    ) {
        public static HcPlanMonthResponse from(HcPlan plan) {
            return new HcPlanMonthResponse(
                    plan.getPlanMonth(),
                    plan.getPlanYear(),
                    plan.getPlannedHires(),
                    plan.getPlannedExits(),
                    plan.getPlannedBillableHc(),
                    plan.getPlannedBenchHc(),
                    plan.getPlannedSupportHc(),
                    plan.getPlannedLeadershipHc(),
                    plan.getPlannedManagementHc());
        }
    }

    public record SalaryBudgetMonthResponse(
            int planMonth,
            int planYear,
            BigDecimal billableSalaries,
            BigDecimal benchSalaries,
            BigDecimal supportSalaries,
            BigDecimal cofoundersSalaries,
            BigDecimal seniorMgmtSalaries
    ) {
        public static SalaryBudgetMonthResponse from(SalaryBudget budget) {
            return new SalaryBudgetMonthResponse(
                    budget.getPlanMonth(),
                    budget.getPlanYear(),
                    budget.getBillableSalaries(),
                    budget.getBenchSalaries(),
                    budget.getSupportSalaries(),
                    budget.getCofoundersSalaries(),
                    budget.getSeniorMgmtSalaries());
        }
    }

    public record ClientRevenuePlanResponse(
            UUID customerId,
            int planMonth,
            int planYear,
            BigDecimal plannedTmRevenue,
            BigDecimal plannedFixedBidRevenue
    ) {
        public static ClientRevenuePlanResponse from(ClientRevenuePlan plan) {
            return new ClientRevenuePlanResponse(
                    plan.getCustomerId(),
                    plan.getPlanMonth(),
                    plan.getPlanYear(),
                    plan.getPlannedTmRevenue(),
                    plan.getPlannedFixedBidRevenue());
        }
    }

    public record OverheadBudgetResponse(
            int planMonth,
            int planYear,
            String overheadLine,
            BigDecimal amount
    ) {
        public static OverheadBudgetResponse from(OverheadBudget budget) {
            return new OverheadBudgetResponse(
                    budget.getPlanMonth(),
                    budget.getPlanYear(),
                    budget.getOverheadLine(),
                    budget.getAmount());
        }
    }

    public record OverheadLineItemResponse(
            String lineCode,
            String category,
            String displayName,
            int sortOrder
    ) {
        public static OverheadLineItemResponse from(OverheadLineItem item) {
            return new OverheadLineItemResponse(
                    item.getLineCode(),
                    item.getCategory(),
                    item.getDisplayName(),
                    item.getSortOrder());
        }
    }
}
