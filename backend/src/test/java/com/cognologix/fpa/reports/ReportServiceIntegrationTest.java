package com.cognologix.fpa.reports;

import com.cognologix.fpa.budgeting.BudgetingService;
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
class ReportServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ReportService reportService;
    @Autowired BudgetingService budgetingService;
    @Autowired CustomerService customerService;
    @Autowired ForecastTypeRepository forecastTypeRepository;
    @Autowired ForecastVersionRepository forecastVersionRepository;

    private FinancialYearPlan plan;

    @BeforeEach
    void seed() {
        customerService.createCustomer(
                "STD" + System.nanoTime(),
                "Standard Report Client",
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
    void allSixStandardReports_produceNonEmptyXlsxWithExpectedSheets() throws Exception {
        assertReport(ReportService.ReportKind.PL, PeriodGranularity.MONTHLY, 4, 2026,
                "How to Read This Report", "P&L Summary", "Monthly Trend");
        assertReport(ReportService.ReportKind.BU_MARGIN, PeriodGranularity.MONTHLY, 4, 2026,
                "How to Read This Report", "BU Summary", "Position Breakdown", "Internal BUs");
        assertReport(ReportService.ReportKind.HEADCOUNT, PeriodGranularity.MONTHLY, 4, 2026,
                "How to Read This Report", "By Category", "By Practice Unit", "Monthly Trend");
        assertReport(ReportService.ReportKind.COST_PER_EMPLOYEE, PeriodGranularity.MONTHLY, 4, 2026,
                "How to Read This Report", "Billable", "Bench", "Support", "Leadership");
        assertReport(ReportService.ReportKind.ROLLING_FORECAST, PeriodGranularity.ANNUAL, null, null,
                "How to Read This Report", "Revenue Forecast", "HC Forecast", "Cost Forecast");
        assertReport(ReportService.ReportKind.EXPENSE_SUMMARY, PeriodGranularity.MONTHLY, 4, 2026,
                "How to Read This Report", "By Category Group", "Line Item Detail");

        // Legend + Sign Convention on P&L Summary; Color Guide / Metric Definitions on How to Read
        ReportService.ReportFile pl = reportService.generate(
                ReportService.ReportKind.PL, plan.getId(), PeriodGranularity.MONTHLY, 4, 2026, null);
        try (var wb = WorkbookFactory.create(new ByteArrayInputStream(pl.bytes()))) {
            var howTo = wb.getSheet("How to Read This Report");
            boolean foundColorGuide = false;
            boolean foundMetricDefs = false;
            for (int i = 0; i <= howTo.getLastRowNum(); i++) {
                var row = howTo.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }
                String v = row.getCell(0).getStringCellValue();
                if (v != null && v.contains("5. Color Guide")) {
                    foundColorGuide = true;
                }
                if (v != null && v.contains("6. Metric Definitions")) {
                    foundMetricDefs = true;
                }
            }
            assertThat(foundColorGuide).isTrue();
            assertThat(foundMetricDefs).isTrue();

            var summary = wb.getSheet("P&L Summary");
            assertThat(summary.getRow(3).getCell(5).getStringCellValue()).isEqualTo("Sign Convention");
            assertThat(summary.getRow(4).getCell(5).getStringCellValue()).contains("Higher");
            assertThat(summary.getRow(5).getCell(5).getStringCellValue()).contains("Lower");
            boolean foundLegend = false;
            for (int i = 0; i <= summary.getLastRowNum(); i++) {
                var row = summary.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }
                if ("Color Guide:".equals(row.getCell(0).getStringCellValue())) {
                    foundLegend = true;
                    break;
                }
            }
            assertThat(foundLegend).isTrue();
        }
    }

    private void assertReport(
            ReportService.ReportKind kind,
            PeriodGranularity granularity,
            Integer month,
            Integer year,
            String... expectedSheets) throws Exception {
        ReportService.ReportFile file = reportService.generate(kind, plan.getId(), granularity, month, year, null);
        assertThat(file.filename()).endsWith(".xlsx");
        assertThat(file.bytes()).isNotEmpty();
        try (var wb = WorkbookFactory.create(new ByteArrayInputStream(file.bytes()))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(expectedSheets.length);
            for (int i = 0; i < expectedSheets.length; i++) {
                assertThat(wb.getSheetAt(i).getSheetName()).isEqualTo(expectedSheets[i]);
            }
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("How to Read This Report");
            assertThat(wb.getSheetAt(1).getRow(0).getCell(0).getStringCellValue())
                    .contains("Cognologix");
        }
    }
}
