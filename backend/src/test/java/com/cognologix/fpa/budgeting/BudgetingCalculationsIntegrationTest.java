package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.repository.*;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.people.PeriodFinalisedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
class BudgetingCalculationsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired BudgetingService budgetingService;
    @Autowired CustomerService customerService;
    @Autowired ForecastTypeRepository forecastTypeRepository;
    @Autowired ForecastVersionRepository forecastVersionRepository;
    @Autowired FinancialYearPlanRepository financialYearPlanRepository;
    @Autowired PeriodActualsRepository periodActualsRepository;
    @Autowired PeriodBuActualsRepository periodBuActualsRepository;
    @Autowired ClientRevenueActualRepository clientRevenueActualRepository;
    @Autowired OverheadActualsRepository overheadActualsRepository;
    @Autowired HcPlanRepository hcPlanRepository;
    @Autowired SalaryBudgetRepository salaryBudgetRepository;
    @Autowired ClientRevenuePlanRepository clientRevenuePlanRepository;
    @Autowired OverheadBudgetRepository overheadBudgetRepository;

    private FinancialYearPlan plan;
    private ForecastType normal;
    private UUID clientId;

    @BeforeEach
    void seedPlan() {
        periodBuActualsRepository.deleteAll();
        clientRevenueActualRepository.deleteAll();
        overheadActualsRepository.deleteAll();
        periodActualsRepository.deleteAll();
        overheadBudgetRepository.deleteAll();
        clientRevenuePlanRepository.deleteAll();
        salaryBudgetRepository.deleteAll();
        hcPlanRepository.deleteAll();
        forecastVersionRepository.deleteAll();
        forecastTypeRepository.deleteAll();
        financialYearPlanRepository.deleteAll();

        var customer = customerService.createCustomer(
                "BU" + System.nanoTime(),
                "Metrics Client " + System.nanoTime(),
                null,
                null,
                LifecycleStatus.ACTIVE,
                45);
        clientId = customer.getId();

        plan = budgetingService.createFinancialYearPlan("FY2627", 100);
        normal = forecastTypeRepository
                .findByFinancialYearPlanIdAndTypeName(plan.getId(), ForecastType.NORMAL)
                .orElseThrow();
        var draft = forecastVersionRepository
                .findByForecastTypeIdAndVersionNumber(normal.getId(), 1)
                .orElseThrow();

        budgetingService.upsertHcPlan(plan.getId(), normal.getId(), draft.getId(), List.of(
                hc(4, 2026, 50),
                hc(5, 2026, 55)));
        budgetingService.upsertSalaryBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                salary(4, 2026, "500000", "50000", "40000", "100000", "80000"),
                salary(5, 2026, "550000", "55000", "40000", "100000", "80000")));
        budgetingService.upsertRevenuePlan(plan.getId(), normal.getId(), draft.getId(), List.of(
                revenue(clientId, 4, 2026, "1000000", "200000"),
                revenue(clientId, 5, 2026, "1100000", "200000")));
        budgetingService.upsertOverheadBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                overhead(4, 2026, "office_rent", "100000"),
                overhead(4, 2026, "staff_medical", "20000"),
                overhead(4, 2026, "training_upskilling", "10000"),
                overhead(5, 2026, "office_rent", "100000"),
                overhead(5, 2026, "staff_medical", "20000"),
                overhead(5, 2026, "training_upskilling", "10000")));

        budgetingService.publishForecastVersion(draft.getId(), "test");
    }

    @Test
    void rollingForecast_usesActualsForFinalisedMonthsAndPlanForFuture() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of(new PeriodFinalisedEvent.BuPeriodActual(
                        customerService.findCustomerRef(clientId).orElseThrow().customerCode(),
                        40, bd("400000")))));

        var rf = budgetingService.getRollingForecast(plan.getId());
        assertThat(rf.months()).hasSize(12);

        var apr = rf.months().stream().filter(m -> m.month() == 4 && m.year() == 2026).findFirst().orElseThrow();
        var may = rf.months().stream().filter(m -> m.month() == 5 && m.year() == 2026).findFirst().orElseThrow();

        assertThat(apr.fromActuals()).isTrue();
        assertThat(apr.hc().billableHc()).isEqualTo(45);
        assertThat(apr.salary().billable()).isEqualByComparingTo("450000");

        assertThat(may.fromActuals()).isFalse();
        assertThat(may.hc().billableHc()).isEqualTo(55);
        assertThat(may.totalRevenue()).isEqualByComparingTo("1300000");
    }

    @Test
    void delta_equalsRollingMinusBaseline_withCorrectSign() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var delta = budgetingService.getDelta(plan.getId());
        var apr = delta.months().stream().filter(m -> m.month() == 4 && m.year() == 2026).findFirst().orElseThrow();

        // No revenue actuals → RF revenue 0 − plan 1.2M = below plan (bad for revenue)
        assertThat(apr.totalRevenue()).isEqualByComparingTo("-1200000");
        // Billable salary: 450k − 500k = under cost plan (good for costs)
        assertThat(apr.salary().billable()).isEqualByComparingTo("-50000");

        var may = delta.months().stream().filter(m -> m.month() == 5 && m.year() == 2026).findFirst().orElseThrow();
        assertThat(may.totalRevenue()).isEqualByComparingTo("0");
        assertThat(may.hc().billableHc()).isZero();
    }

    @Test
    void planVsActual_varianceIsActualMinusPlan() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var pva = budgetingService.getPlanVsActual(plan.getId());
        var apr = pva.months().stream().filter(m -> m.month() == 4 && m.year() == 2026).findFirst().orElseThrow();

        assertThat(apr.hasActuals()).isTrue();
        assertThat(apr.hc().plan().billableHc()).isEqualTo(50);
        assertThat(apr.hc().actual().billableHc()).isEqualTo(45);
        assertThat(apr.hc().variance().billableHc()).isEqualTo(-5);

        assertThat(apr.totalSalaryCost().plan()).isEqualByComparingTo("770000");
        assertThat(apr.totalSalaryCost().actual()).isEqualByComparingTo("685000");
        assertThat(apr.totalSalaryCost().variance()).isEqualByComparingTo("-85000");

        assertThat(pva.q1()).isNotNull();
        assertThat(pva.fy()).isNotNull();
    }

    @Test
    void costPerEmployee_layer3AllocatedToBillableOnly() {
        budgetingService.upsertOverheadActuals(plan.getId(), 4, 2026, List.of(
                OverheadActuals.builder().overheadLine("office_rent").actualAmount(bd("100000")).build(),
                OverheadActuals.builder().overheadLine("staff_medical").actualAmount(bd("20000")).build(),
                OverheadActuals.builder().overheadLine("training_upskilling").actualAmount(bd("10000")).build()
        ), "test");

        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                50, 10, 10, 5, 5,
                bd("500000"), bd("80000"), bd("60000"), bd("90000"), bd("100000"),
                List.of()));

        var cost = budgetingService.getCostPerEmployee(plan.getId(), 4, 2026);
        assertThat(cost.fromActuals()).isTrue();
        assertThat(cost.billable().layer3()).isGreaterThan(BigDecimal.ZERO);
        assertThat(cost.bench().layer3()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.support().layer3()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.leadership().layer3()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cost.totalCostPerBillableHead()).isEqualByComparingTo(cost.billable().total());
        // Layer3 = office_rent 100000 / 50 billable HC
        assertThat(cost.billable().layer3()).isEqualByComparingTo("2000.00");
    }

    @Test
    void buMetrics_grossMarginCalculation() {
        var code = customerService.findCustomerRef(clientId).orElseThrow().customerCode();
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                50, 10, 8, 6, 4,
                bd("500000"), bd("50000"), bd("40000"), bd("80000"), bd("100000"),
                List.of(new PeriodFinalisedEvent.BuPeriodActual(code, 40, bd("400000")))));

        budgetingService.upsertRevenueActuals(plan.getId(), 4, 2026, bd("1100000"),
                List.of(ClientRevenueActual.builder().customerId(clientId).actualRevenue(bd("1100000")).build()),
                "test");

        var metrics = budgetingService.getBuMetrics(plan.getId(), 4, 2026);
        var row = metrics.rows().stream()
                .filter(r -> clientId.equals(r.customerId()))
                .findFirst()
                .orElseThrow();

        assertThat(row.plannedRevenue()).isEqualByComparingTo("1200000");
        assertThat(row.actualRevenue()).isEqualByComparingTo("1100000");
        assertThat(row.actualSalaryCost()).isEqualByComparingTo("400000");
        assertThat(row.actualGrossMargin()).isEqualByComparingTo("700000");
        assertThat(row.actualGrossMarginPct()).isEqualByComparingTo("63.64");
    }

    private static HcPlan hc(int month, int year, int billable) {
        return HcPlan.builder()
                .planMonth(month).planYear(year)
                .plannedHires(0).plannedExits(0)
                .plannedBillableHc(billable).plannedBenchHc(10)
                .plannedSupportHc(8).plannedLeadershipHc(6).plannedManagementHc(4)
                .build();
    }

    private static SalaryBudget salary(int month, int year, String b, String be, String s, String c, String sm) {
        return SalaryBudget.builder()
                .planMonth(month).planYear(year)
                .billableSalaries(bd(b)).benchSalaries(bd(be)).supportSalaries(bd(s))
                .cofoundersSalaries(bd(c)).seniorMgmtSalaries(bd(sm))
                .build();
    }

    private static ClientRevenuePlan revenue(UUID customerId, int month, int year, String tm, String fb) {
        return ClientRevenuePlan.builder()
                .customerId(customerId).planMonth(month).planYear(year)
                .plannedTmRevenue(bd(tm)).plannedFixedBidRevenue(bd(fb))
                .build();
    }

    private static OverheadBudget overhead(int month, int year, String line, String amount) {
        return OverheadBudget.builder()
                .planMonth(month).planYear(year).overheadLine(line).amount(bd(amount))
                .build();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
