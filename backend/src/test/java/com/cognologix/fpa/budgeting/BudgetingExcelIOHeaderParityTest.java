package com.cognologix.fpa.budgeting;

import com.cognologix.fpa.budgeting.domain.ClientRevenuePlan;
import com.cognologix.fpa.budgeting.domain.HcPlan;
import com.cognologix.fpa.budgeting.domain.OverheadBudget;
import com.cognologix.fpa.budgeting.domain.SalaryBudget;
import com.cognologix.fpa.customer.CustomerService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetingExcelIOHeaderParityTest {

    @Mock CustomerService customerService;

    BudgetingExcelIO excelIO;

    @BeforeEach
    void setUp() {
        excelIO = new BudgetingExcelIO(customerService);
    }

    @Test
    void hcPlan_exportHeadersMatchSample_andRoundTrip() throws Exception {
        assertHeadersEqual(
                excelIO.exportHcPlan(List.of()),
                excelIO.buildHcPlanSample(),
                List.of("Month", "Year", "Planned Hires", "Planned Exits",
                        "Planned Billable HC", "Planned Bench HC", "Planned Support HC",
                        "Planned Leadership HC", "Planned Management HC"));

        byte[] exported = excelIO.exportHcPlan(List.of(HcPlan.builder()
                .planMonth(4).planYear(2026)
                .plannedHires(2).plannedExits(1)
                .plannedBillableHc(50).plannedBenchHc(10)
                .plannedSupportHc(8).plannedLeadershipHc(6).plannedManagementHc(4)
                .build()));
        var parsed = excelIO.parseHcPlan(multipart(exported, "hc.xlsx"));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().getPlanMonth()).isEqualTo(4);
        assertThat(parsed.rows().getFirst().getPlannedHires()).isEqualTo(2);
    }

    @Test
    void salaryBudget_exportHeadersMatchSample_andRoundTrip() throws Exception {
        assertHeadersEqual(
                excelIO.exportSalaryBudget(List.of()),
                excelIO.buildSalaryBudgetSample(),
                List.of("Month", "Year", "Billable Salaries", "Bench Salaries",
                        "Support Salaries", "Cofounders Salaries", "Senior Mgmt Salaries"));

        byte[] exported = excelIO.exportSalaryBudget(List.of(SalaryBudget.builder()
                .planMonth(5).planYear(2026)
                .billableSalaries(new BigDecimal("10.00"))
                .benchSalaries(new BigDecimal("1.00"))
                .supportSalaries(new BigDecimal("2.00"))
                .cofoundersSalaries(new BigDecimal("3.00"))
                .seniorMgmtSalaries(new BigDecimal("4.00"))
                .build()));
        var parsed = excelIO.parseSalaryBudget(multipart(exported, "salary.xlsx"));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().getBillableSalaries()).isEqualByComparingTo("10.00");
    }

    @Test
    void revenuePlan_exportHeadersMatchSample_andRoundTrip() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.findCustomerRef(customerId))
                .thenReturn(Optional.of(new CustomerService.CustomerRef(
                        customerId, "ACME", "Acme Corp", false)));
        when(customerService.resolveBuCustomer("ACME"))
                .thenReturn(Optional.of(new CustomerService.BuCustomerRef(
                        customerId, "ACME", "Acme Corp", false)));

        assertHeadersEqual(
                excelIO.exportRevenuePlan(List.of()),
                excelIO.buildRevenuePlanSample(),
                List.of("Month", "Year", "Customer Code", "Customer Name",
                        "Planned TM Revenue", "Planned Fixed Bid Revenue"));

        byte[] exported = excelIO.exportRevenuePlan(List.of(ClientRevenuePlan.builder()
                .customerId(customerId)
                .planMonth(7).planYear(2026)
                .plannedTmRevenue(new BigDecimal("100.00"))
                .plannedFixedBidRevenue(new BigDecimal("50.00"))
                .build()));
        var parsed = excelIO.parseRevenuePlan(multipart(exported, "rev.xlsx"));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().getCustomerId()).isEqualTo(customerId);
        assertThat(parsed.rows().getFirst().getPlannedTmRevenue()).isEqualByComparingTo("100.00");
        assertThat(parsed.rows().getFirst().getPlannedFixedBidRevenue()).isEqualByComparingTo("50.00");
    }

    @Test
    void overheadBudget_exportHeadersMatchSample_andRoundTrip() throws Exception {
        assertHeadersEqual(
                excelIO.exportOverheadBudget(List.of()),
                excelIO.buildOverheadBudgetSample(),
                List.of("Month", "Year", "Overhead Line", "Amount"));

        byte[] exported = excelIO.exportOverheadBudget(List.of(OverheadBudget.builder()
                .planMonth(6).planYear(2026)
                .overheadLine("office_rent")
                .amount(new BigDecimal("12.50"))
                .build()));
        var parsed = excelIO.parseOverheadBudget(multipart(exported, "oh.xlsx"));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().getOverheadLine()).isEqualTo("office_rent");
    }

    @Test
    void hcPlan_importAcceptsPlanMonthAliasHeaders() throws Exception {
        // Backup-style headers (snake_case plan_month) must still parse via normalize + alias.
        byte[] file = workbookBytes(List.of(
                "fiscal_year", "type_name", "version_number", "plan_month", "plan_year",
                "planned_hires", "planned_exits", "planned_billable_hc", "planned_bench_hc",
                "planned_support_hc", "planned_leadership_hc", "planned_management_hc"),
                List.of(List.of("FY2627", "NORMAL", "1", "4", "2026", "2", "1", "50", "10", "8", "6", "4")));
        var parsed = excelIO.parseHcPlan(multipart(file, "backup_style.xlsx"));
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().getPlanMonth()).isEqualTo(4);
        assertThat(parsed.rows().getFirst().getPlanYear()).isEqualTo(2026);
    }

    private static void assertHeadersEqual(byte[] exportBytes, byte[] sampleBytes, List<String> expected)
            throws Exception {
        assertThat(readHeaders(exportBytes)).containsExactlyElementsOf(expected);
        assertThat(readHeaders(sampleBytes)).containsExactlyElementsOf(expected);
    }

    private static List<String> readHeaders(byte[] content) throws Exception {
        try (var in = new ByteArrayInputStream(content);
             var wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                headers.add(header.getCell(c).getStringCellValue());
            }
            return headers;
        }
    }

    private static MockMultipartFile multipart(byte[] content, String name) {
        return new MockMultipartFile(
                "file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content);
    }

    private static byte[] workbookBytes(List<String> headers, List<List<String>> rows) throws Exception {
        try (var wb = WorkbookFactory.create(true); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
