package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import com.cognologix.fpa.people.domain.SystemAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack HTTP integration test: real Spring context, real PostgreSQL (Testcontainers),
 * real controllers and service layer. Only {@link CustomerService} is mocked (cross-module boundary).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Testcontainers
@RecordApplicationEvents
class PeoplePayrollEndToEndIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CustomerService customerService;

    @Test
    void uploadViaHttp_buildMaster_finalise_publishesEvent(ApplicationEvents events) throws Exception {
        when(customerService.isKnownCustomer(anyString())).thenReturn(true);

        // 1. Create period
        var periodJson = mockMvc.perform(post("/api/people/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":7,\"periodYear\":2026}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode period = objectMapper.readTree(periodJson);
        UUID periodId = UUID.fromString(period.get("id").asText());
        UUID versionId = UUID.fromString(period.get("versions").get(0).get("id").asText());

        // 2. Create column mapping templates
        UUID peopleMappingId = createMapping("ZOHO_PEOPLE", "People Default", peopleMappingBody());
        UUID payrollMappingId = createMapping("ZOHO_PAYROLL", "Payroll Default", payrollMappingBody());

        // 3. Upload People snapshot (3 rows) via multipart HTTP
        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(xlsx("people.xlsx",
                                List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                                List.of(
                                        List.of("EMP100", "Ada Lovelace", "Product Engineering", "Icertis", "Y"),
                                        List.of("EMP101", "Bob Builder", "Product Engineering", "Icertis", "N"),
                                        List.of("EMP102", "Carol Support", "HR", "Internal", "N"))))
                        .param("import_type", "ZOHO_PEOPLE")
                        .param("mapping_id", peopleMappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsImported").value(3))
                .andExpect(jsonPath("$.periodVersionStatus").value("OPEN"));

        // 4. Upload Payroll snapshot (3 rows — 2 matched, 1 orphan) via multipart HTTP
        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(xlsx("payroll.xlsx",
                                List.of("Employee No", "Name", "Gross", "Net"),
                                List.of(
                                        List.of("EMP100", "Ada Lovelace", "120000", "90000"),
                                        List.of("EMP101", "Bob Builder", "100000", "75000"),
                                        List.of("PAY999", "Orphan Payroll", "50000", "40000"))))
                        .param("import_type", "ZOHO_PAYROLL")
                        .param("mapping_id", payrollMappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsImported").value(3))
                .andExpect(jsonPath("$.periodVersionStatus").value("SNAPSHOTS_UPLOADED"));

        // 5. Build master via HTTP
        var masterJson = mockMvc.perform(
                        post("/api/people/periods/{p}/versions/{v}/build-master", periodId, versionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode masters = objectMapper.readTree(masterJson);
        assertThat(masters).hasSize(4);

        List<String> statuses = new java.util.ArrayList<>();
        masters.forEach(node -> statuses.add(node.get("reconciliationStatus").asText()));
        assertThat(statuses).containsExactlyInAnyOrder(
                ReconciliationStatus.MATCHED.name(),
                ReconciliationStatus.MATCHED.name(),
                ReconciliationStatus.PAYROLL_PENDING.name(),
                ReconciliationStatus.UNMATCHED.name());

        // 6. Finalise via HTTP
        mockMvc.perform(post("/api/people/periods/{p}/versions/{v}/finalise", periodId, versionId))
                .andExpect(status().isNoContent());

        // 7. Assert finalised version state via HTTP
        mockMvc.perform(get("/api/people/periods/{p}/versions/{v}", periodId, versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALISED"))
                .andExpect(jsonPath("$.isLatestFinalised").value(true));

        // 8. Assert PeriodFinalisedEvent published (ADR-022)
        assertThat(events.stream(PeriodFinalisedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.periodVersionId()).isEqualTo(versionId);
                    assertThat(event.billableHeadcount()).isEqualTo(1);
                    assertThat(event.benchHeadcount()).isEqualTo(1);
                    assertThat(event.supportHeadcount()).isEqualTo(1);
                });

        // 9. Master records still queryable via HTTP after finalise
        mockMvc.perform(get("/api/people/master/{id}", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void exitedUpload_linksRegistryRowsToSnapshotDetail() throws Exception {
        // 1. Create period
        var periodJson = mockMvc.perform(post("/api/people/periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":8,\"periodYear\":2026}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode period = objectMapper.readTree(periodJson);
        UUID versionId = UUID.fromString(period.get("versions").get(0).get("id").asText());

        // 2. Seed employee registry via People upload
        UUID peopleMappingId = createMapping("ZOHO_PEOPLE", "People Default", peopleMappingBody());
        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(xlsx("people.xlsx",
                                List.of("Employee ID", "Name", "PU", "BU", "Billable"),
                                List.of(List.of("EMP200", "Exiting Employee", "Engineering", "Icertis", "Y"))))
                        .param("import_type", "ZOHO_PEOPLE")
                        .param("mapping_id", peopleMappingId.toString()))
                .andExpect(status().isOk());

        // 3. Upload exited employees file
        UUID exitedMappingId = createMapping(
                "ZOHO_PEOPLE_EXITED",
                "Exited Default",
                exitedMappingBody());
        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(xlsx("exited.xlsx",
                                List.of("Employee ID", "Last Working Day"),
                                List.of(List.of("EMP200", "2026-08-15"))))
                        .param("import_type", "ZOHO_PEOPLE_EXITED")
                        .param("mapping_id", exitedMappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsImported").value(1));

        // 4. Snapshot detail returns registry row linked to this upload
        mockMvc.perform(get("/api/people/imports/{id}/snapshots/ZOHO_PEOPLE_EXITED", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importType").value("ZOHO_PEOPLE_EXITED"))
                .andExpect(jsonPath("$.exitedRegistryRows.length()").value(1))
                .andExpect(jsonPath("$.exitedRegistryRows[0].employeeId").value("EMP200"))
                .andExpect(jsonPath("$.exitedRegistryRows[0].fullName").value("Exiting Employee"))
                .andExpect(jsonPath("$.exitedRegistryRows[0].exitDate").value("2026-08-15"))
                .andExpect(jsonPath("$.exitedRegistryRows[0].exitDatePrecision").value("DAY_LEVEL"))
                .andExpect(jsonPath("$.exitedRegistryRows[0].exitStatus").value("EXITED"));
    }

    private UUID createMapping(String importType, String templateName, String linesJson) throws Exception {
        var body = """
                {"importType":"%s","templateName":"%s","lines":%s}
                """.formatted(importType, templateName, linesJson);
        var response = mockMvc.perform(post("/api/people/imports/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private static String peopleMappingBody() {
        return """
                [
                  {"excelColumnName":"Employee ID","systemAttribute":"%s"},
                  {"excelColumnName":"Name","systemAttribute":"%s"},
                  {"excelColumnName":"PU","systemAttribute":"%s"},
                  {"excelColumnName":"BU","systemAttribute":"%s"},
                  {"excelColumnName":"Billable","systemAttribute":"%s"}
                ]
                """.formatted(
                SystemAttribute.EMPLOYEE_ID,
                SystemAttribute.FULL_NAME,
                SystemAttribute.PRACTICE_UNIT,
                SystemAttribute.BUSINESS_UNIT,
                SystemAttribute.BILLABLE_STATUS);
    }

    private static String payrollMappingBody() {
        return """
                [
                  {"excelColumnName":"Employee No","systemAttribute":"%s"},
                  {"excelColumnName":"Name","systemAttribute":"%s"},
                  {"excelColumnName":"Gross","systemAttribute":"%s"},
                  {"excelColumnName":"Net","systemAttribute":"%s"}
                ]
                """.formatted(
                SystemAttribute.EMPLOYEE_NO,
                SystemAttribute.FULL_NAME,
                SystemAttribute.GROSS_PAY,
                SystemAttribute.NET_PAY);
    }

    private static String exitedMappingBody() {
        return """
                [
                  {"excelColumnName":"Employee ID","systemAttribute":"%s"},
                  {"excelColumnName":"Last Working Day","systemAttribute":"%s"}
                ]
                """.formatted(
                SystemAttribute.EMPLOYEE_ID,
                SystemAttribute.LAST_WORKING_DAY);
    }

    private static MockMultipartFile xlsx(
            String filename, List<String> headers, List<List<String>> rows) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
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
                    "file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
