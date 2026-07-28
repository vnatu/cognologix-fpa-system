package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.ForecastType;
import com.cognologix.fpa.budgeting.domain.ForecastVersionStatus;
import com.cognologix.fpa.budgeting.domain.HcPlan;
import com.cognologix.fpa.budgeting.domain.OverheadBudget;
import com.cognologix.fpa.budgeting.domain.SalaryBudget;
import com.cognologix.fpa.budgeting.repository.ClientRevenuePlanRepository;
import com.cognologix.fpa.budgeting.repository.FinancialYearPlanRepository;
import com.cognologix.fpa.budgeting.repository.ForecastTypeRepository;
import com.cognologix.fpa.budgeting.repository.ForecastVersionRepository;
import com.cognologix.fpa.budgeting.repository.HcPlanRepository;
import com.cognologix.fpa.budgeting.repository.OverheadActualsRepository;
import com.cognologix.fpa.budgeting.repository.OverheadBudgetRepository;
import com.cognologix.fpa.budgeting.repository.PeriodActualsRepository;
import com.cognologix.fpa.budgeting.repository.SalaryBudgetRepository;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.expenses.ExpenseService;
import com.cognologix.fpa.expenses.dto.ExpenseDtos.ExpenseEntryRequest;
import com.cognologix.fpa.expenses.repository.ExpenseActualRepository;
import com.cognologix.fpa.people.PeriodFinalisedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestSecurityConfig.class, BudgetingServiceIntegrationTest.SyncAsyncConfig.class})
@Testcontainers
class BudgetingServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired BudgetingService budgetingService;
    @Autowired ForecastTypeRepository forecastTypeRepository;
    @Autowired ForecastVersionRepository forecastVersionRepository;
    @Autowired PeriodActualsRepository periodActualsRepository;
    @Autowired FinancialYearPlanRepository financialYearPlanRepository;
    @Autowired HcPlanRepository hcPlanRepository;
    @Autowired SalaryBudgetRepository salaryBudgetRepository;
    @Autowired ClientRevenuePlanRepository clientRevenuePlanRepository;
    @Autowired OverheadBudgetRepository overheadBudgetRepository;
    @Autowired OverheadActualsRepository overheadActualsRepository;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseActualRepository expenseActualRepository;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAll() {
        // period_bu_actuals cascades from period_actuals via ON DELETE CASCADE
        overheadActualsRepository.deleteAll();
        expenseActualRepository.deleteAll();
        periodActualsRepository.deleteAll();
        hcPlanRepository.deleteAll();
        salaryBudgetRepository.deleteAll();
        clientRevenuePlanRepository.deleteAll();
        overheadBudgetRepository.deleteAll();
        forecastVersionRepository.deleteAll();
        forecastTypeRepository.deleteAll();
        financialYearPlanRepository.deleteAll();
    }

    @Test
    void createFinancialYearPlan_seedsThreeTypesWithDraftV1() {
        var plan = budgetingService.createFinancialYearPlan("FY2627", 120);

        assertThat(plan.getFiscalYear()).isEqualTo("FY2627");
        assertThat(plan.getFiscalYearStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(plan.getFiscalYearEnd()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(plan.getOpeningHc()).isEqualTo(120);

        var types = forecastTypeRepository.findByFinancialYearPlanId(plan.getId());
        assertThat(types).hasSize(3);
        assertThat(types).extracting(ForecastType::getTypeName)
                .containsExactlyInAnyOrder(
                        ForecastType.NORMAL, ForecastType.AGGRESSIVE, ForecastType.CONSERVATIVE);
        assertThat(types.stream().filter(ForecastType::isPrimary)).hasSize(1)
                .first()
                .extracting(ForecastType::getTypeName)
                .isEqualTo(ForecastType.NORMAL);

        for (ForecastType type : types) {
            var versions = forecastVersionRepository.findByForecastTypeIdOrderByVersionNumberAsc(type.getId());
            assertThat(versions).hasSize(1);
            assertThat(versions.getFirst().getVersionNumber()).isEqualTo(1);
            assertThat(versions.getFirst().getStatus()).isEqualTo(ForecastVersionStatus.DRAFT);
        }
    }

    @Test
    void publishForecastVersion_setsActiveAndSupersedesPrior() {
        var plan = budgetingService.createFinancialYearPlan("FY2627", 100);
        var normal = forecastTypeRepository
                .findByFinancialYearPlanIdAndTypeName(plan.getId(), ForecastType.NORMAL)
                .orElseThrow();
        var v1 = forecastVersionRepository
                .findByForecastTypeIdAndVersionNumber(normal.getId(), 1)
                .orElseThrow();

        budgetingService.publishForecastVersion(v1.getId(), "finance-user");

        var activeV1 = forecastVersionRepository.findById(v1.getId()).orElseThrow();
        assertThat(activeV1.getStatus()).isEqualTo(ForecastVersionStatus.ACTIVE);
        assertThat(activeV1.getPublishedBy()).isEqualTo("finance-user");
        assertThat(activeV1.getPublishedAt()).isNotNull();
        assertThat(budgetingService.getActiveBaseline(plan.getId())).isPresent()
                .get()
                .extracting(v -> v.getId())
                .isEqualTo(v1.getId());

        var v2 = budgetingService.createDraftVersion(normal.getId(), "finance-user");
        budgetingService.publishForecastVersion(v2.getId(), "finance-user");

        var supersededV1 = forecastVersionRepository.findById(v1.getId()).orElseThrow();
        var activeV2 = forecastVersionRepository.findById(v2.getId()).orElseThrow();
        assertThat(supersededV1.getStatus()).isEqualTo(ForecastVersionStatus.SUPERSEDED);
        assertThat(supersededV1.getSupersededAt()).isNotNull();
        assertThat(supersededV1.getSupersededBy()).isEqualTo("finance-user");
        assertThat(activeV2.getStatus()).isEqualTo(ForecastVersionStatus.ACTIVE);
        assertThat(budgetingService.getActiveBaseline(plan.getId())).isPresent()
                .get()
                .extracting(v -> v.getId())
                .isEqualTo(v2.getId());
    }

    @Test
    void upsertPlanInputs_secondSaveUpdatesWithoutDuplicateKey() {
        var plan = budgetingService.createFinancialYearPlan("FY2627", 100);
        var normal = forecastTypeRepository
                .findByFinancialYearPlanIdAndTypeName(plan.getId(), ForecastType.NORMAL)
                .orElseThrow();
        var draft = forecastVersionRepository
                .findByForecastTypeIdAndVersionNumber(normal.getId(), 1)
                .orElseThrow();

        var hcFirst = List.of(HcPlan.builder()
                .planMonth(4).planYear(2026)
                .plannedHires(2).plannedExits(1)
                .plannedBillableHc(50).plannedBenchHc(10)
                .plannedSupportHc(8).plannedLeadershipHc(6).plannedManagementHc(4)
                .build());
        budgetingService.upsertHcPlan(plan.getId(), normal.getId(), draft.getId(), hcFirst);

        var hcSecond = List.of(HcPlan.builder()
                .planMonth(4).planYear(2026)
                .plannedHires(5).plannedExits(2)
                .plannedBillableHc(55).plannedBenchHc(8)
                .plannedSupportHc(8).plannedLeadershipHc(6).plannedManagementHc(4)
                .build());
        budgetingService.upsertHcPlan(plan.getId(), normal.getId(), draft.getId(), hcSecond);

        var savedHc = hcPlanRepository.findByForecastVersionId(draft.getId());
        assertThat(savedHc).hasSize(1);
        assertThat(savedHc.getFirst().getPlannedHires()).isEqualTo(5);
        assertThat(savedHc.getFirst().getPlannedBillableHc()).isEqualTo(55);

        budgetingService.upsertSalaryBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                SalaryBudget.builder()
                        .planMonth(4).planYear(2026)
                        .billableSalaries(new BigDecimal("100000"))
                        .benchSalaries(new BigDecimal("20000"))
                        .supportSalaries(new BigDecimal("15000"))
                        .cofoundersSalaries(new BigDecimal("30000"))
                        .seniorMgmtSalaries(new BigDecimal("25000"))
                        .build()));
        budgetingService.upsertSalaryBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                SalaryBudget.builder()
                        .planMonth(4).planYear(2026)
                        .billableSalaries(new BigDecimal("110000"))
                        .benchSalaries(new BigDecimal("21000"))
                        .supportSalaries(new BigDecimal("16000"))
                        .cofoundersSalaries(new BigDecimal("31000"))
                        .seniorMgmtSalaries(new BigDecimal("26000"))
                        .build()));
        assertThat(budgetingService.getSalaryBudget(plan.getId(), normal.getId(), draft.getId()))
                .hasSize(1);
        assertThat(budgetingService.getSalaryBudget(plan.getId(), normal.getId(), draft.getId())
                .getFirst().getBillableSalaries()).isEqualByComparingTo("110000");

        budgetingService.upsertOverheadBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                OverheadBudget.builder()
                        .planMonth(4).planYear(2026)
                        .overheadLine("office_rent")
                        .amount(new BigDecimal("50000"))
                        .build()));
        budgetingService.upsertOverheadBudget(plan.getId(), normal.getId(), draft.getId(), List.of(
                OverheadBudget.builder()
                        .planMonth(4).planYear(2026)
                        .overheadLine("office_rent")
                        .amount(new BigDecimal("55000"))
                        .build()));
        assertThat(budgetingService.getOverheadBudget(plan.getId(), normal.getId(), draft.getId()))
                .hasSize(1);
        assertThat(budgetingService.getOverheadBudget(plan.getId(), normal.getId(), draft.getId())
                .getFirst().getAmount()).isEqualByComparingTo("55000");

        UUID officeRentId = expenseService.listAllCategories().stream()
                .filter(c -> "office_rent".equals(c.lineCode()))
                .findFirst()
                .orElseThrow()
                .id();
        expenseService.saveMonthlyExpenses(4, 2026, List.of(
                new ExpenseEntryRequest(officeRentId, new BigDecimal("48000"), null)), "test");
        expenseService.saveMonthlyExpenses(4, 2026, List.of(
                new ExpenseEntryRequest(officeRentId, new BigDecimal("49000"), null)), "test");
        var actuals = expenseService.getMonthlyExpenseActuals(4, 2026);
        assertThat(actuals).containsKey("office_rent");
        assertThat(actuals.get("office_rent")).isEqualByComparingTo("49000");
    }

    @Test
    void applicationModuleListener_writesPeriodActualsFromPeriodFinalisedEvent() {
        var plan = budgetingService.createFinancialYearPlan("FY2627", 100);
        UUID periodVersionId = UUID.randomUUID();

        var event = new PeriodFinalisedEvent(
                periodVersionId,
                4,
                2026,
                50, 10, 8, 6, 4,
                new BigDecimal("500000.00"),
                new BigDecimal("80000.00"),
                new BigDecimal("60000.00"),
                new BigDecimal("90000.00"),
                new BigDecimal("120000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                new BigDecimal("80000.00"),
                new BigDecimal("60000.00"),
                new BigDecimal("90000.00"),
                new BigDecimal("120000.00"),
                List.of(new PeriodFinalisedEvent.BuPeriodActual(
                        "BU-A", 20, new BigDecimal("200000.00"), BigDecimal.ZERO, new BigDecimal("200000.00"))));

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(event));

        var actuals = periodActualsRepository
                .findByFinancialYearPlanIdAndActualsMonthAndActualsYear(plan.getId(), 4, 2026)
                .orElseThrow();

        assertThat(actuals.getActualBillableHc()).isEqualTo(50);
        assertThat(actuals.getActualBenchHc()).isEqualTo(10);
        assertThat(actuals.getActualSupportHc()).isEqualTo(8);
        assertThat(actuals.getActualLeadershipHc()).isEqualTo(6);
        assertThat(actuals.getActualManagementHc()).isEqualTo(4);
        assertThat(actuals.getActualTotalHc()).isEqualTo(78);
        assertThat(actuals.getActualBillableSalaries()).isEqualByComparingTo("500000.00");
        assertThat(actuals.getActualBenchSalaries()).isEqualByComparingTo("80000.00");
        assertThat(actuals.getActualSupportSalaries()).isEqualByComparingTo("60000.00");
        assertThat(actuals.getActualLeadershipSalaries()).isEqualByComparingTo("90000.00");
        assertThat(actuals.getActualManagementSalaries()).isEqualByComparingTo("120000.00");
        assertThat(actuals.getPeoplePeriodVersionId()).isEqualTo(periodVersionId);
    }

    @TestConfiguration
    static class SyncAsyncConfig {
        /** Make @ApplicationModuleListener's @Async run inline so tests are deterministic. */
        @Bean
        @Primary
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean(name = "applicationTaskExecutor")
        Executor applicationTaskExecutor(TaskExecutor taskExecutor) {
            return taskExecutor;
        }
    }
}
