package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.PeoplePayrollService;

import java.math.BigDecimal;
import java.util.List;

public record MasterSummaryResponse(
        ClassificationTotals billable,
        ClassificationTotals bench,
        ClassificationTotals support,
        ClassificationTotals leadership,
        ClassificationTotals management,
        List<BuBreakdownResponse> byBusinessUnit
) {
    public static MasterSummaryResponse from(PeoplePayrollService.MasterSummary s) {
        return new MasterSummaryResponse(
                new ClassificationTotals(s.billableHc(), s.billableGrossPay()),
                new ClassificationTotals(s.benchHc(), s.benchGrossPay()),
                new ClassificationTotals(s.supportHc(), s.supportGrossPay()),
                new ClassificationTotals(s.leadershipHc(), s.leadershipGrossPay()),
                new ClassificationTotals(s.managementHc(), s.managementGrossPay()),
                s.buBreakdown().stream()
                        .map(b -> new BuBreakdownResponse(b.businessUnit(), b.billableHc(), b.totalGrossPay()))
                        .toList());
    }

    public record ClassificationTotals(int headcount, BigDecimal grossPay) {}

    public record BuBreakdownResponse(String businessUnit, int billableHc, BigDecimal totalGrossPay) {}
}
