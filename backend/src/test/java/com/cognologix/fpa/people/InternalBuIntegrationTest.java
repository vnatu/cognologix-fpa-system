package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import com.cognologix.fpa.customer.repository.CustomerRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests for internal BU model (ADR-029) — real CustomerService, no mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
class InternalBuIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;
    @Autowired PeriodVersionRepository periodVersionRepository;

    private static int nextPeriodMonth = 8;

    @Test
    void billableLeadershipMember_hasIsBillableAndIsLeadership() throws Exception {
        UUID versionId = createOpenPeriod();

        var peopleMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PEOPLE,
                "People",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee ID", SystemAttribute.EMPLOYEE_ID),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("PU", SystemAttribute.PRACTICE_UNIT),
                        new PeoplePayrollService.MappingLineInput("BU", SystemAttribute.BUSINESS_UNIT),
                        new PeoplePayrollService.MappingLineInput("Billable", SystemAttribute.BILLABLE_STATUS)));

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
                        List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP200", "Leader", "Product Engineering", "Leadership", "Y"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(
                        List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP200", "Leader", "200000", "150000"))),
                "tester");

        var masters = peoplePayrollService.buildMasterRecords(versionId);
        assertThat(masters).hasSize(1);
        assertThat(masters.getFirst().isLeadership()).isTrue();
        assertThat(masters.getFirst().isBillable()).isTrue();
    }

    @Test
    void billableEmployeeWithProjectCode_setsBillingCustomerCode() throws Exception {
        var client = customerService.createCustomer(
                "ICERTI", "Icertis", null, null, LifecycleStatus.ACTIVE, 45);
        customerService.addProjectCode(client.getId(), "PROJ-ICERTI", "Main");

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
                        List.of(List.of("EMP300", "Billable Dev", "Product Engineering", "Icertis", "Y", "PROJ-ICERTI"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(
                        List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP300", "Billable Dev", "150000", "120000"))),
                "tester");

        var masters = peoplePayrollService.buildMasterRecords(versionId);
        assertThat(masters).hasSize(1);
        assertThat(masters.getFirst().isBillable()).isTrue();
        assertThat(masters.getFirst().getBillingCustomerCode()).isEqualTo("ICERTI");
    }

    @Test
    void internalBuCode_doesNotGenerateUnrecognisedBuWarning() throws Exception {
        assertThat(customerRepository.findByCustomerCode("MGMT")).isPresent();

        UUID versionId = createOpenPeriod();

        var peopleMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PEOPLE,
                "People",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee ID", SystemAttribute.EMPLOYEE_ID),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("PU", SystemAttribute.PRACTICE_UNIT),
                        new PeoplePayrollService.MappingLineInput("BU", SystemAttribute.BUSINESS_UNIT),
                        new PeoplePayrollService.MappingLineInput("Billable", SystemAttribute.BILLABLE_STATUS)));

        var payrollMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PAYROLL,
                "Payroll",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee No", SystemAttribute.EMPLOYEE_NO),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("Gross", SystemAttribute.GROSS_PAY),
                        new PeoplePayrollService.MappingLineInput("Net", SystemAttribute.NET_PAY)));

        var upload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(
                        List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP400", "Mgmt Staff", "Product Engineering", "MGMT", "N"))),
                "tester");

        assertThat(upload.unrecognizedBuCodes()).isEmpty();

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(
                        List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP400", "Mgmt Staff", "100000", "80000"))),
                "tester");

        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(xlsx(
                                List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                                List.of(List.of("EMP401", "Mgmt Two", "Product Engineering", "MGMT", "N"))))
                        .param("import_type", "ZOHO_PEOPLE")
                        .param("mapping_id", peopleMapping.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unrecognizedBuCodes").isEmpty());
    }

    private UUID createOpenPeriod() {
        int month = nextPeriodMonth++;
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
