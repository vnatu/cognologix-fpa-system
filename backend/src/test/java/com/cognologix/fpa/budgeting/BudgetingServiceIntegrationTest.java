package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.ForecastType;
import com.cognologix.fpa.budgeting.domain.ForecastVersionStatus;
import com.cognologix.fpa.budgeting.repository.FinancialYearPlanRepository;
import com.cognologix.fpa.budgeting.repository.ForecastTypeRepository;
import com.cognologix.fpa.budgeting.repository.ForecastVersionRepository;
import com.cognologix.fpa.budgeting.repository.PeriodActualsRepository;
import com.cognologix.fpa.config.TestSecurityConfig;
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
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanAll() {
        // period_bu_actuals cascades from period_actuals via ON DELETE CASCADE
        periodActualsRepository.deleteAll();
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
                List.of(new PeriodFinalisedEvent.BuPeriodActual("BU-A", 20, new BigDecimal("200000.00"))));

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
