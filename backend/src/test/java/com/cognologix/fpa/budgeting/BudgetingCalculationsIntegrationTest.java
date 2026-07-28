package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.*;
import com.cognologix.fpa.budgeting.repository.*;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.expenses.ExpenseService;
import com.cognologix.fpa.expenses.dto.ExpenseDtos.ExpenseEntryRequest;
import com.cognologix.fpa.expenses.repository.ExpenseActualRepository;
import com.cognologix.fpa.people.EmployeeRegistry;
import com.cognologix.fpa.people.PeriodFinalisedEvent;
import com.cognologix.fpa.people.domain.ExitStatus;
import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.PeopleSnapshot;
import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import com.cognologix.fpa.people.domain.SnapshotUpload;
import com.cognologix.fpa.people.repository.EmployeeRegistryRepository;
import com.cognologix.fpa.people.repository.MasterRecordRepository;
import com.cognologix.fpa.people.repository.PeopleSnapshotRepository;
import com.cognologix.fpa.people.repository.PeriodRepository;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import com.cognologix.fpa.people.repository.SnapshotUploadRepository;
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
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseActualRepository expenseActualRepository;
    @Autowired HcPlanRepository hcPlanRepository;
    @Autowired SalaryBudgetRepository salaryBudgetRepository;
    @Autowired ClientRevenuePlanRepository clientRevenuePlanRepository;
    @Autowired OverheadBudgetRepository overheadBudgetRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PeriodRepository periodRepository;
    @Autowired PeriodVersionRepository periodVersionRepository;
    @Autowired MasterRecordRepository masterRecordRepository;
    @Autowired EmployeeRegistryRepository employeeRegistryRepository;
    @Autowired SnapshotUploadRepository snapshotUploadRepository;
    @Autowired PeopleSnapshotRepository peopleSnapshotRepository;

    private FinancialYearPlan plan;
    private ForecastType normal;
    private UUID clientId;

    @BeforeEach
    void seedPlan() {
        periodBuActualsRepository.deleteAll();
        clientRevenueActualRepository.deleteAll();
        overheadActualsRepository.deleteAll();
        expenseActualRepository.deleteAll();
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
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of(new PeriodFinalisedEvent.BuPeriodActual(
                        customerService.findCustomerRef(clientId).orElseThrow().customerCode(),
                        40, bd("400000"), BigDecimal.ZERO, bd("400000")))));

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
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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
        saveExpenseActuals(4, 2026,
                entry("office_rent", "100000"),
                entry("staff_medical", "20000"),
                entry("training_upskilling", "10000"));

        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                50, 10, 10, 5, 5,
                bd("500000"), bd("80000"), bd("60000"), bd("90000"), bd("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
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

    private ExpenseEntryRequest entry(String lineCode, String amount) {
        UUID categoryId = expenseService.listAllCategories().stream()
                .filter(c -> lineCode.equals(c.lineCode()))
                .findFirst()
                .orElseThrow()
                .id();
        return new ExpenseEntryRequest(categoryId, bd(amount), null);
    }

    private void saveExpenseActuals(int month, int year, ExpenseEntryRequest... entries) {
        expenseService.saveMonthlyExpenses(month, year, List.of(entries), "test");
    }

    @Test
    void buMetrics_grossMarginCalculation() {
        var code = customerService.findCustomerRef(clientId).orElseThrow().customerCode();
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                50, 10, 8, 6, 4,
                bd("500000"), bd("50000"), bd("40000"), bd("80000"), bd("100000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("500000"), bd("50000"), bd("40000"), bd("80000"), bd("100000"),
                List.of(new PeriodFinalisedEvent.BuPeriodActual(code, 40, bd("400000"), BigDecimal.ZERO, bd("400000")))));

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

    @Test
    void buAnalysis_splitsExternalAndInternalWithPositionBreakdown() {
        var external = customerService.findCustomerRef(clientId).orElseThrow();
        var internalCust = customerService.createCustomer(
                "INT" + System.nanoTime(),
                "Leadership Internal " + System.nanoTime(),
                null, null, LifecycleStatus.ACTIVE, null);
        internalCust.setInternal(true);
        customerRepository.save(internalCust);

        periodRepository.findByPeriodMonthAndPeriodYear(4, 2026).ifPresent(existing -> {
            for (PeriodVersion v : periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(existing.getId())) {
                masterRecordRepository.findByPeriodVersionId(v.getId())
                        .forEach(masterRecordRepository::delete);
                peopleSnapshotRepository.findByPeriodVersionId(v.getId())
                        .forEach(peopleSnapshotRepository::delete);
                snapshotUploadRepository.findByPeriodVersionId(v.getId())
                        .forEach(snapshotUploadRepository::delete);
                periodVersionRepository.delete(v);
            }
            periodRepository.delete(existing);
        });

        Period period = periodRepository.save(Period.builder()
                .periodMonth(4).periodYear(2026).build());
        PeriodVersion version = periodVersionRepository.save(PeriodVersion.builder()
                .period(period).versionNumber(1)
                .status(PeriodStatus.FINALISED).latestFinalised(true)
                .createdBy("test").build());

        saveMasterForBu(version, external.customerCode(), true, "100000", "Senior Engineer");
        saveMasterForBu(version, external.customerCode(), true, "200000", "Senior Engineer");
        saveMasterForBu(version, external.customerCode(), false, "80000", "Analyst");
        saveMasterForBu(version, internalCust.getCustomerCode(), false, "300000", "VP Engineering");

        budgetingService.upsertRevenueActuals(plan.getId(), 4, 2026, bd("900000"),
                List.of(ClientRevenueActual.builder()
                        .customerId(clientId).actualRevenue(bd("900000")).build()),
                "test");

        var analysis = budgetingService.getBuAnalysis(
                plan.getId(), PeriodGranularity.MONTHLY, 4, 2026, null);

        assertThat(analysis.totalCompanyHc()).isEqualTo(4);
        assertThat(analysis.totalCompanyPayrollCost()).isEqualByComparingTo("680000");
        assertThat(analysis.totalCompanyRevenue()).isEqualByComparingTo("900000");

        assertThat(analysis.externalBUs()).hasSize(1);
        var ext = analysis.externalBUs().getFirst();
        assertThat(ext.customerCode()).isEqualTo(external.customerCode());
        assertThat(ext.totalHc()).isEqualTo(3);
        assertThat(ext.billableHc()).isEqualTo(2);
        assertThat(ext.nonBillableHc()).isEqualTo(1);
        assertThat(ext.totalPayrollCost()).isEqualByComparingTo("380000");
        assertThat(ext.actualRevenue()).isEqualByComparingTo("900000");
        assertThat(ext.grossMargin()).isEqualByComparingTo("520000");
        assertThat(ext.buCostPctOfTotal()).isEqualByComparingTo("55.88");
        assertThat(ext.buRevenuePctOfTotal()).isEqualByComparingTo("100.00");
        assertThat(ext.positionBreakdown()).isNotEmpty();
        assertThat(ext.positionBreakdown().getFirst().title()).isEqualTo("Senior Engineer");
        assertThat(ext.positionBreakdown().getFirst().headcount()).isEqualTo(2);

        assertThat(analysis.internalBUs()).hasSize(1);
        var intl = analysis.internalBUs().getFirst();
        assertThat(intl.customerCode()).isEqualTo(internalCust.getCustomerCode());
        assertThat(intl.totalHc()).isEqualTo(1);
        assertThat(intl.totalPayrollCost()).isEqualByComparingTo("300000");
        assertThat(intl.buCostPctOfTotal()).isEqualByComparingTo("44.12");
    }

    private void saveMasterForBu(
            PeriodVersion version, String businessUnit, boolean billable, String grossPay, String title) {
        EmployeeRegistry registry = employeeRegistryRepository.save(EmployeeRegistry.builder()
                .employeeId("BUA-" + System.nanoTime())
                .fullName("BU Analysis Emp")
                .exitStatus(ExitStatus.ACTIVE)
                .build());
        SnapshotUpload upload = snapshotUploadRepository.save(SnapshotUpload.builder()
                .periodVersion(version)
                .importType(ImportType.ZOHO_PEOPLE)
                .originalFilename("test.xlsx")
                .uploadedBy("test")
                .rowCount(1)
                .build());
        PeopleSnapshot people = peopleSnapshotRepository.save(PeopleSnapshot.builder()
                .snapshotUpload(upload)
                .periodVersion(version)
                .employeeId(registry.getEmployeeId())
                .fullName(registry.getFullName())
                .practiceUnit("Product Engineering")
                .businessUnit(businessUnit)
                .billableStatus(billable ? "Y" : "N")
                .title(title)
                .build());
        masterRecordRepository.save(MasterRecord.builder()
                .periodVersion(version)
                .employeeRegistry(registry)
                .peopleSnapshot(people)
                .practiceUnit("Product Engineering")
                .businessUnit(businessUnit)
                .billableStatus(billable ? "Y" : "N")
                .grossPay(new BigDecimal(grossPay))
                .billable(billable)
                .bench(!billable)
                .support(false)
                .leadership(false)
                .management(false)
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .builtBy("test")
                .build());
    }

    @Test
    void planVsActual_monthlySelectedPeriod_matchesSingleMonth() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var pva = budgetingService.getPlanVsActual(
                plan.getId(), null, PeriodGranularity.MONTHLY, 4, 2026, null);

        assertThat(pva.granularity()).isEqualTo("MONTHLY");
        assertThat(pva.periodLabel()).isEqualTo("April 2026");
        assertThat(pva.selectedPeriod().totalSalaryCost().plan()).isEqualByComparingTo("770000");
        assertThat(pva.selectedPeriod().totalSalaryCost().actual()).isEqualByComparingTo("685000");
        assertThat(pva.actualsCoverageNote()).isNull();
    }

    @Test
    void planVsActual_annualYtd_excludesMonthsWithoutActuals() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var pva = budgetingService.getPlanVsActual(
                plan.getId(), null, PeriodGranularity.ANNUAL, null, null, null);

        assertThat(pva.granularity()).isEqualTo("ANNUAL");
        assertThat(pva.periodLabel()).isEqualTo("FY2627");
        assertThat(pva.monthsWithActuals()).isEqualTo(1);
        assertThat(pva.actualsCoverageNote()).contains("1 of 12 months");
        // YTD plan = April only (May has plan but no actuals)
        assertThat(pva.selectedPeriod().totalSalaryCost().plan()).isEqualByComparingTo("770000");
        assertThat(pva.selectedPeriod().totalSalaryCost().actual()).isEqualByComparingTo("685000");
    }

    @Test
    void planVsActual_quarterlySumsThreeMonths() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var pva = budgetingService.getPlanVsActual(
                plan.getId(), null, PeriodGranularity.QUARTERLY, null, 2026, 1);

        assertThat(pva.periodLabel()).isEqualTo("Q1 FY2627");
        // Apr plan 770k + May plan 825k + Jun plan 0 = 1,595,000
        assertThat(pva.selectedPeriod().totalSalaryCost().plan()).isEqualByComparingTo("1595000");
    }

    @Test
    void delta_periodTotal_sumsSelectedMonths() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var delta = budgetingService.getDelta(plan.getId(), PeriodGranularity.MONTHLY, 4, 2026, null);
        assertThat(delta.periodTotal().salary().billable()).isEqualByComparingTo("-50000");
        assertThat(delta.months()).hasSize(12);
    }

    @Test
    void findLatestActualsMonth_returnsMostRecentInFyOrder() {
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 4, 2026,
                45, 5, 8, 6, 4,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("450000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));
        budgetingService.onPeriodFinalised(new PeriodFinalisedEvent(
                UUID.randomUUID(), 5, 2026,
                50, 5, 8, 6, 4,
                bd("500000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                bd("500000"), bd("40000"), bd("35000"), bd("70000"), bd("90000"),
                List.of()));

        var latest = budgetingService.findLatestActualsMonth(plan.getId());
        assertThat(latest).isPresent();
        assertThat(latest.get().getMonthValue()).isEqualTo(5);
        assertThat(latest.get().getYear()).isEqualTo(2026);
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
