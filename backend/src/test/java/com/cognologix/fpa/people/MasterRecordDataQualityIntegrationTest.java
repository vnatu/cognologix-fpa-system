package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.SystemAttribute;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data quality flag tests for master record build (ADR-029 extension).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class MasterRecordDataQualityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired CustomerService customerService;
    @Autowired PeriodVersionRepository periodVersionRepository;

    private static int nextPeriodMonth = 1;
    private static int nextCustomerSuffix = 1;

    @Test
    void externalBu_blankProjectCode_flagsMissingProjectCode() throws Exception {
        String customerCode = uniqueCustomerCode();
        customerService.createCustomer(
                customerCode, "Icertis " + customerCode, null, null, LifecycleStatus.ACTIVE, 45);
        UUID versionId = createOpenPeriodWithSnapshots(
                List.of("EMP501", "External No Proj", "Product Engineering", customerCode, "Y", ""),
                true);

        var master = peoplePayrollService.buildMasterRecords(versionId).getFirst();
        assertThat(master.getDataQualityFlags()).contains("MISSING_PROJECT_CODE");
    }

    @Test
    void externalBu_unknownProjectCode_flagsProjectCodeNotFound() throws Exception {
        String customerCode = uniqueCustomerCode();
        customerService.createCustomer(
                customerCode, "Icertis " + customerCode, null, null, LifecycleStatus.ACTIVE, 45);
        UUID versionId = createOpenPeriodWithSnapshots(
                List.of("EMP502", "External Bad Proj", "Product Engineering", customerCode, "Y", "UNKNOWN-PROJ"),
                true);

        var master = peoplePayrollService.buildMasterRecords(versionId).getFirst();
        assertThat(master.getDataQualityFlags()).contains("PROJECT_CODE_NOT_FOUND");
    }

    @Test
    void internalBu_blankProjectCode_noProjectCodeFlag() throws Exception {
        UUID versionId = createOpenPeriodWithSnapshots(
                List.of("EMP503", "Mgmt Staff", "Product Engineering", "MGMT", "N", ""),
                true);

        var master = peoplePayrollService.buildMasterRecords(versionId).getFirst();
        assertThat(master.getDataQualityFlags()).isNull();
    }

    @Test
    void billableEmployee_unresolvedBillingClient_flagsBillingClientUnresolved() throws Exception {
        String customerCode = uniqueCustomerCode();
        customerService.createCustomer(
                customerCode, "Icertis " + customerCode, null, null, LifecycleStatus.ACTIVE, 45);
        UUID versionId = createOpenPeriodWithSnapshots(
                List.of("EMP504", "Billable No Client", "Product Engineering", customerCode, "Y", ""),
                true);

        var master = peoplePayrollService.buildMasterRecords(versionId).getFirst();
        assertThat(master.getDataQualityFlags()).contains("BILLING_CLIENT_UNRESOLVED");
        assertThat(master.getBillingCustomerCode()).isNull();
    }

    private static String uniqueCustomerCode() {
        return "EXT" + String.format("%03d", nextCustomerSuffix++);
    }

    private UUID createOpenPeriodWithSnapshots(List<String> peopleRow, boolean includePayroll)
            throws Exception {
        UUID versionId = createOpenPeriod();

        var peopleMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PEOPLE,
                "People",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee ID", SystemAttribute.EMPLOYEE_ID),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("PU", SystemAttribute.PRACTICE_UNIT),
                        new PeoplePayrollService.MappingLineInput("BU", SystemAttribute.BUSINESS_UNIT),
                        new PeoplePayrollService.MappingLineInput("Billable", SystemAttribute.BILLABLE_STATUS),
                        new PeoplePayrollService.MappingLineInput("Project", SystemAttribute.PROJECT_CODE)));

        var payrollMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PAYROLL,
                "Payroll",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee No", SystemAttribute.EMPLOYEE_NO),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("Gross", SystemAttribute.GROSS_PAY),
                        new PeoplePayrollService.MappingLineInput("Net", SystemAttribute.NET_PAY)));

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(
                        List.of("Employee ID", "Name", "PU", "BU", "Billable", "Project"),
                        List.of(peopleRow)),
                "tester");

        if (includePayroll) {
            peoplePayrollService.uploadSnapshotFile(
                    versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                    xlsx(
                            List.of("Employee No", "Name", "Gross", "Net"),
                            List.of(List.of(peopleRow.getFirst(), peopleRow.get(1), "150000", "120000"))),
                    "tester");
        }

        return versionId;
    }

    private UUID createOpenPeriod() {
        int month = ((nextPeriodMonth++ - 1) % 12) + 1;
        var period = peoplePayrollService.createPeriod(month, 2026);
        return periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst()
                .getId();
    }

    private static MockMultipartFile xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            var headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                headerRow.createCell(c).setCellValue(headers.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "upload.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
