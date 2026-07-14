package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.*;
import com.cognologix.fpa.people.repository.PayrollSnapshotRepository;
import com.cognologix.fpa.people.repository.PeopleSnapshotRepository;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReuploadIntegrationTest extends PeopleModuleIntegrationTest {

    @Autowired PeoplePayrollService peoplePayrollService;
    @Autowired PeriodVersionRepository periodVersionRepository;
    @Autowired PeopleSnapshotRepository peopleSnapshotRepository;
    @Autowired PayrollSnapshotRepository payrollSnapshotRepository;

    @Test
    void reuploadPayrollOnly_supersedesVersionAndCreatesIncrementedVersion() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(8, 2027);
        UUID v1Id = periodVersionRepository
                .findByPeriodIdOrderByVersionNumberDesc(period.getId())
                .getFirst()
                .getId();

        var payrollMapping = peoplePayrollService.saveMappingTemplate(
                ImportType.ZOHO_PAYROLL,
                "Payroll",
                List.of(
                        new PeoplePayrollService.MappingLineInput("Employee No", SystemAttribute.EMPLOYEE_NO),
                        new PeoplePayrollService.MappingLineInput("Name", SystemAttribute.FULL_NAME),
                        new PeoplePayrollService.MappingLineInput("Gross", SystemAttribute.GROSS_PAY),
                        new PeoplePayrollService.MappingLineInput("Net", SystemAttribute.NET_PAY)));

        peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("1004", "Employee One", "120000", "90000"))),
                "tester");

        var reupload = peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("1004", "Employee One Revised", "130000", "95000"))),
                "tester");

        var v1 = periodVersionRepository.findById(v1Id).orElseThrow();
        assertThat(v1.getStatus()).isEqualTo(PeriodStatus.SUPERSEDED);

        var versions = periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(period.getId());
        assertThat(versions).hasSize(2);
        var v2 = versions.getFirst();
        assertThat(v2.getVersionNumber()).isEqualTo(2);
        assertThat(reupload.periodVersionId()).isEqualTo(v2.getId());

        assertThat(payrollSnapshotRepository.findByPeriodVersionId(v1Id)).hasSize(1);
        assertThat(payrollSnapshotRepository.findByPeriodVersionId(v2.getId())).hasSize(1);
        assertThat(payrollSnapshotRepository.findByPeriodVersionId(v2.getId()).getFirst().getFullName())
                .isEqualTo("Employee One Revised");
    }

    @Test
    void reuploadSameImportType_supersedesVersionAndCreatesIncrementedVersion() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(11, 2025);
        UUID v1Id = periodVersionRepository
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
                v1Id, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP100", "Ada", "Product Engineering", "Icertis", "Y"))),
                "tester");

        peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Ada", "120000", "90000"))),
                "tester");

        var reupload = peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP100", "Ada Revised", "Product Engineering", "Icertis", "Y"))),
                "tester");

        var v1 = periodVersionRepository.findById(v1Id).orElseThrow();
        assertThat(v1.getStatus()).isEqualTo(PeriodStatus.SUPERSEDED);

        var versions = periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(period.getId());
        assertThat(versions).hasSize(2);
        var v2 = versions.getFirst();
        assertThat(v2.getVersionNumber()).isEqualTo(2);
        assertThat(v2.getStatus()).isEqualTo(PeriodStatus.OPEN);
        assertThat(reupload.periodVersionId()).isEqualTo(v2.getId());

        assertThat(peopleSnapshotRepository.findByPeriodVersionId(v1Id)).hasSize(1);
        assertThat(peopleSnapshotRepository.findByPeriodVersionId(v2.getId())).hasSize(1);
        assertThat(peopleSnapshotRepository.findByPeriodVersionId(v2.getId()).getFirst().getFullName())
                .isEqualTo("Ada Revised");
    }

    @Test
    void firstUploadOfDifferentImportType_doesNotBumpVersion() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(12, 2025);
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
                        List.of(List.of("EMP100", "Ada", "Product Engineering", "Icertis", "Y"))),
                "tester");

        var payrollUpload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Ada", "120000", "90000"))),
                "tester");

        assertThat(payrollUpload.periodVersionId()).isEqualTo(versionId);
        assertThat(periodVersionRepository.findByPeriodIdOrderByVersionNumberDesc(period.getId()))
                .hasSize(1);
        assertThat(periodVersionRepository.findById(versionId).orElseThrow().getStatus())
                .isEqualTo(PeriodStatus.SNAPSHOTS_UPLOADED);
    }

    @Test
    void reuploadAgainstFinalisedVersion_rejectsWith400Message() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(1, 2027);
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
                        List.of(List.of("EMP100", "Ada", "Product Engineering", "Icertis", "Y"))),
                "tester");
        peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Ada", "120000", "90000"))),
                "tester");
        peoplePayrollService.buildMasterRecords(versionId);
        peoplePayrollService.finalisePeriod(versionId, "tester");

        assertThatThrownBy(() -> peoplePayrollService.uploadSnapshotFile(
                        versionId, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                        xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                                List.of(List.of("EMP100", "Ada", "Product Engineering", "Icertis", "Y"))),
                        "tester"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This period is finalised. Use 'New Version' to make corrections.");
    }

    @Test
    void buildMaster_usesSnapshotsFromSpecifiedVersionOnly() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(2, 2027);
        UUID v1Id = periodVersionRepository
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
                v1Id, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP100", "Original", "Product Engineering", "Icertis", "Y"))),
                "tester");
        peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Original", "120000", "90000"))),
                "tester");

        var v1Masters = peoplePayrollService.buildMasterRecords(v1Id);
        assertThat(v1Masters).hasSize(1);
        assertThat(v1Masters.getFirst().getPeopleSnapshot().getFullName()).isEqualTo("Original");

        var reupload = peoplePayrollService.uploadSnapshotFile(
                v1Id, ImportType.ZOHO_PEOPLE, peopleMapping.getId(),
                xlsx(List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                        List.of(List.of("EMP100", "Revised", "Product Engineering", "Icertis", "Y"))),
                "tester");
        UUID v2Id = reupload.periodVersionId();

        assertThat(periodVersionRepository.findById(v1Id).orElseThrow().getStatus())
                .isEqualTo(PeriodStatus.SUPERSEDED);

        peoplePayrollService.uploadSnapshotFile(
                v2Id, ImportType.ZOHO_PAYROLL, payrollMapping.getId(),
                xlsx(List.of("Employee No", "Name", "Gross", "Net"),
                        List.of(List.of("EMP100", "Revised", "130000", "95000"))),
                "tester");

        var v2Masters = peoplePayrollService.buildMasterRecords(v2Id);
        assertThat(v2Masters).hasSize(1);
        assertThat(v2Masters.getFirst().getPeopleSnapshot().getFullName()).isEqualTo("Revised");
        assertThat(v2Masters.getFirst().getPayrollSnapshot().getGrossPay())
                .isEqualByComparingTo("130000");
    }

    @Test
    void exitedUpload_acceptsCommonDateFormats() throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        var period = peoplePayrollService.createPeriod(9, 2027);
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
                        List.of(List.of("EMP400", "Exiting Colleague", "Engineering", "Icertis", "Y"))),
                "tester");

        var upload = peoplePayrollService.uploadSnapshotFile(
                versionId, ImportType.ZOHO_PEOPLE_EXITED, exitedMapping.getId(),
                xlsx(List.of("Employee ID", "Last Working Day"),
                        List.of(List.of("EMP400", "15/06/2026"))),
                "tester");

        assertThat(upload.rowsImported()).isEqualTo(1);
        var detail = peoplePayrollService.getSnapshotDetail(versionId, ImportType.ZOHO_PEOPLE_EXITED);
        assertThat(detail.exitedRegistryRows()).hasSize(1);
        assertThat(detail.exitedRegistryRows().getFirst().exitDate())
                .isEqualTo(LocalDate.of(2026, 6, 15));
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
