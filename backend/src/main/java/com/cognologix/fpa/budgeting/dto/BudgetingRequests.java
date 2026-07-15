package com.cognologix.fpa.budgeting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request bodies for Budgeting REST API. */
public final class BudgetingRequests {

    private BudgetingRequests() {}

    public record CreatePlanRequest(
            @NotBlank @Size(max = 10) String fiscalYear,
            @NotNull @Min(0) Integer openingHc
    ) {}

    public record UpsertHcPlanRequest(
            @NotEmpty @Valid List<HcPlanMonthRequest> months
    ) {}

    public record HcPlanMonthRequest(
            @NotNull @Min(1) @Max(12) Integer planMonth,
            @NotNull @Min(2000) Integer planYear,
            @NotNull @Min(0) Integer plannedHires,
            @NotNull @Min(0) Integer plannedExits,
            @NotNull @Min(0) Integer plannedBillableHc,
            @NotNull @Min(0) Integer plannedBenchHc,
            @NotNull @Min(0) Integer plannedSupportHc,
            @NotNull @Min(0) Integer plannedLeadershipHc,
            @NotNull @Min(0) Integer plannedManagementHc
    ) {}

    public record UpsertSalaryBudgetRequest(
            @NotEmpty @Valid List<SalaryBudgetMonthRequest> months
    ) {}

    public record SalaryBudgetMonthRequest(
            @NotNull @Min(1) @Max(12) Integer planMonth,
            @NotNull @Min(2000) Integer planYear,
            @NotNull @DecimalMin("0") BigDecimal billableSalaries,
            @NotNull @DecimalMin("0") BigDecimal benchSalaries,
            @NotNull @DecimalMin("0") BigDecimal supportSalaries,
            @NotNull @DecimalMin("0") BigDecimal cofoundersSalaries,
            @NotNull @DecimalMin("0") BigDecimal seniorMgmtSalaries
    ) {}

    public record UpsertRevenuePlanRequest(
            @NotEmpty @Valid List<ClientRevenueMonthRequest> entries
    ) {}

    public record ClientRevenueMonthRequest(
            @NotNull UUID customerId,
            @NotNull @Min(1) @Max(12) Integer planMonth,
            @NotNull @Min(2000) Integer planYear,
            @NotNull @DecimalMin("0") BigDecimal plannedTmRevenue,
            @NotNull @DecimalMin("0") BigDecimal plannedFixedBidRevenue
    ) {}

    public record UpsertOverheadBudgetRequest(
            @NotEmpty @Valid List<OverheadBudgetMonthRequest> entries
    ) {}

    public record OverheadBudgetMonthRequest(
            @NotNull @Min(1) @Max(12) Integer planMonth,
            @NotNull @Min(2000) Integer planYear,
            @NotBlank @Size(max = 100) String overheadLine,
            @NotNull @DecimalMin("0") BigDecimal amount
    ) {}

    public record UpsertRevenueActualsRequest(
            @DecimalMin("0") BigDecimal totalRevenue,
            @Valid List<ClientRevenueActualRequest> byClient
    ) {}

    public record ClientRevenueActualRequest(
            @NotNull UUID customerId,
            @NotNull @DecimalMin("0") BigDecimal actualRevenue
    ) {}

    public record UpsertOverheadActualsRequest(
            @NotEmpty @Valid List<OverheadActualLineRequest> lines
    ) {}

    public record OverheadActualLineRequest(
            @NotBlank @Size(max = 100) String overheadLine,
            @NotNull @DecimalMin("0") BigDecimal actualAmount
    ) {}
}
