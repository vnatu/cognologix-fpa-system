package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.*;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MasterBuildIntegrationTest extends PeopleModuleIntegrationTest {

    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired PeriodVersionRepository periodVersionRepository;

    @Test
    void uploadBothSnapshots_buildMaster_finalise_happyPath() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(6, 2026);
        UUID versionId = periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst()
                .getId();

        var peopleMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PEOPLE,
                "People Default",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee ID", SystemAttribute.EMPLOYEE_ID),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("PU", SystemAttribute.PRACTICE_UNIT),
                        new PeoplePayrollService.MappingLineInput("BU", SystemAttribute.BUSINESS_UNIT),
                        new PeoplePayrollService.MappingLineInput("Billable", SystemAttribute.BILLABLE_STATUS)));

        var payrollMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PAYROLL,
                "Payroll Default",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee No", SystemAttribute.EMPLOYEE_NO),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("Gross", SystemAttribute.GROSS_PAY),
                        new PeoplePayrollService.MappingLineInput("Net", SystemAttribute.NET_PAY)));

        var peopleUpload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable", "Unused Col"),
                        List.of(List.of("EMP100", "Ada", "Product Engineering", "Icertis", "Y", "x"))),
                "tester");
        assertThat(peopleUpload.unmappedColumns()).contains("Unused Col");
        assertThat(peopleUpload.periodVersionStatus()).isEqualTo(PeriodStatus.OPEN);

        var payrollUpload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Ada", "120000", "90000"))),
                "tester");
        assertThat(payrollUpload.periodVersionStatus()).isEqualTo(PeriodStatus.SNAPSHOTS_UPLOADED);

        var masters = peoplePayrollService.buildMasterRecords(versionId);
        assertThat(masters).hasSize(1);
        assertThat(masters.getFirst().getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(masters.getFirst().isBillable()).isTrue();
        assertThat(masters.getFirst().isDeliveryPu()).isTrue();

        peoplePayrollService.finalisePeriod(versionId, "tester");
        var finalised = periodVersionRepository.findById(versionId).orElseThrow();
        assertThat(finalised.getStatus()).isEqualTo(PeriodStatus.FINALISED);
        assertThat(finalised.isLatestFinalised()).isTrue();

        var v2 = peoplePayrollService.createPeriodVersion(period.getId(), "tester");
        assertThat(v2.getVersionNumber()).isEqualTo(2);
        assertThat(v2.getStatus()).isEqualTo(PeriodStatus.OPEN);
    }

    @Test
    void fnfPayroll_reconcilesExitedAndActiveEmployees() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(9, 2026);
        UUID versionId = periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst()
                .getId();

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

        var exitedMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PEOPLE_EXITED,
                "Exited",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee ID", SystemAttribute.EMPLOYEE_ID),
                        new PeoplePayrollService.MappingLineInput(
                                "Last Working Day", SystemAttribute.LAST_WORKING_DAY)));

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(
                                List.of("EMP100", "Active Employee", "Engineering", "Icertis", "Y"),
                                List.of("EMP200", "Exited Employee", "Engineering", "Icertis", "Y"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE_EXITED, exitedMapping.getId(),
                xlsx(List.of("Employee ID", "Last Working Day"),
                        List.of(List.of("EMP200", "2026-09-15"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Active Employee", "120000", "90000"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL_FNF, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(
                                List.of("EMP200", "Exited Employee", "50000", "40000"),
                                List.of("EMP300", "Active FnF Only", "30000", "25000"))),
                "tester");

        peoplePayrollService.registerEmployee("EMP300", "Active FnF Only");

        var masters = peoplePayrollService.buildMasterRecords(versionId);
        assertThat(masters).hasSize(3);

        var byEmployee = masters.stream()
                .collect(java.util.stream.Collectors.toMap(
                        m -> m.getEmployeeRegistry().getEmployeeId(),
                        m -> m,
                        (a, b) -> a));

        assertThat(byEmployee.get("EMP100").getReconciliationStatus())
                .isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(byEmployee.get("EMP200").getReconciliationStatus())
                .isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(byEmployee.get("EMP300").getReconciliationStatus())
                .isEqualTo(ReconciliationStatus.UNMATCHED);
        assertThat(byEmployee.get("EMP300").getPayrollSnapshot().getImportType())
                .isEqualTo(ImportType.ZOHO_PAYROLL_FNF);
    }

    @Test
    void sameEmployeeInRegularAndFnfPayroll_bothRowsPersisted() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(10, 2026);
        UUID versionId = periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst()
                .getId();

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
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("1045", "Same Employee", "Engineering", "Icertis", "Y"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("1045", "Same Employee", "120000", "90000"))),
                "tester");

        var fnfUpload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL_FNF, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("1045", "Same Employee", "50000", "40000"))),
                "tester");

        assertThat(fnfUpload.rowsImported()).isEqualTo(1);

        var masters = peoplePayrollService.buildMasterRecords(versionId);
        assertThat(masters.stream()
                .anyMatch(m -> "1045".equals(m.getEmployeeRegistry().getEmployeeId()))).isTrue();
    }

    private static MockMultipartFile xlsx(List<String> headers, List<List<String>> rows) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                var vals = rows.get(r);
                for (int c = 0; c < vals.size(); c++) {
                    row.createCell(c).setCellValue(vals.get(c));
                }
            }
            wb.write(out);
            return new MockMultipartFile(
                    "file", "snapshot.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
