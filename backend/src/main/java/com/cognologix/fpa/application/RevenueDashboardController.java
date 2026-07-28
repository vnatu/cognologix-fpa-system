package com.cognologix.fpa.application;

import com.cognologix.fpa.budgeting.BudgetingService;
import com.cognologix.fpa.revenue.RevenueService;
import com.cognologix.fpa.revenue.dto.RevenueDtos.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Composes Revenue actuals + Budgeting plans for the Revenue Dashboard without creating a
 * Modulith cycle between the revenue and budgeting modules (ADR-043).
 */
@RestController
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
@Tag(name = "Revenue", description = "Dashboard composition (plan from Budgeting, actuals from Revenue)")
public class RevenueDashboardController {

    private final RevenueService revenueService;
    private final BudgetingService budgetingService;

    @GetMapping("/dashboard/{periodMonth}/{periodYear}")
    @Operation(summary = "Revenue dashboard: vs plan, invoice status, DSO")
    public DashboardResponse getDashboard(
            @PathVariable int periodMonth,
            @PathVariable int periodYear) {
        return revenueService.getDashboard(
                periodMonth,
                periodYear,
                (customerId, month, year) -> budgetingService
                        .getClientRevenuePlan(customerId, month, year)
                        .map(BudgetingService.ClientRevenuePlanView::plannedTotal)
                        .orElse(BigDecimal.ZERO));
    }
}
