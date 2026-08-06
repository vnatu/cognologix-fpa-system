package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.FinancialYearPlan;
import com.cognologix.fpa.budgeting.domain.ForecastType;
import com.cognologix.fpa.budgeting.domain.PeriodGranularity;
import com.cognologix.fpa.budgeting.repository.ForecastTypeRepository;
import com.cognologix.fpa.budgeting.repository.ForecastVersionRepository;
import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
class BudgetingReportServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired BudgetingService budgetingService;
    @Autowired BudgetingReportService budgetingReportService;
    @Autowired CustomerService customerService;
    @Autowired ForecastTypeRepository forecastTypeRepository;
    @Autowired ForecastVersionRepository forecastVersionRepository;

    private FinancialYearPlan plan;

    @BeforeEach
    void seed() {
        customerService.createCustomer(
                "RPT" + System.nanoTime(),
                "Report Client",
                null, null, LifecycleStatus.ACTIVE, 45);
        plan = budgetingService.createFinancialYearPlan("FY2627", 100);
        var normal = forecastTypeRepository
                .findByFinancialYearPlanIdAndTypeName(plan.getId(), ForecastType.NORMAL)
                .orElseThrow();
        var draft = forecastVersionRepository
                .findByForecastTypeIdAndVersionNumber(normal.getId(), 1)
                .orElseThrow();
        budgetingService.publishForecastVersion(draft.getId(), "test");
    }

    @Test
    void allSixReports_includeHowToReadAsFirstSheet() throws Exception {
        for (BudgetingReportService.ReportType type : BudgetingReportService.ReportType.values()) {
            var file = budgetingReportService.generate(
                    plan.getId(), type, PeriodGranularity.MONTHLY, 4, 2026, null, null);
            assertThat(file.filename()).endsWith(".xlsx");
            assertThat(file.bytes()).isNotEmpty();
            try (var wb = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
                assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("How to Read This Report");
                assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                        .isEqualTo("How to Read This Report");
            }
        }
    }
}
