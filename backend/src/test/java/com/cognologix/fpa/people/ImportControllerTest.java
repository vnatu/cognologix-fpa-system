package com.cognologix.fpa.people;

import com.cognologix.fpa.config.TestSecurityConfig;
import com.cognologix.fpa.people.domain.ImportColumnMapping;
import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.dto.SnapshotDetailResponse;
import com.cognologix.fpa.people.dto.SnapshotUploadMetadataResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ImportController.class, PeopleExceptionHandler.class})
@Import(TestSecurityConfig.class)
class ImportControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PeoplePayrollService peoplePayrollService;
    @MockBean ExcelSnapshotParser excelSnapshotParser;

    @Test
    void createMapping_returnsCreated() throws Exception {
        var mapping = ImportColumnMapping.builder()
                .id(UUID.randomUUID())
                .importType(ImportType.ZOHO_PEOPLE)
                .templateName("Default People")
                .active(true)
                .lines(List.of())
                .build();
        when(peoplePayrollService.saveMappingTemplate(eq(ImportType.ZOHO_PEOPLE), anyString(), anyList()))
                .thenReturn(mapping);

        var body = """
                {"importType":"ZOHO_PEOPLE","templateName":"Default People",
                 "lines":[{"excelColumnName":"Employee ID","systemAttribute":"EmployeeID"}]}
                """;
        mockMvc.perform(post("/api/people/imports/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importType").value("ZOHO_PEOPLE"))
                .andExpect(jsonPath("$.templateName").value("Default People"));
    }

    @Test
    void listMappings_returnsOk() throws Exception {
        when(peoplePayrollService.findActiveMappings()).thenReturn(List.of());
        mockMvc.perform(get("/api/people/imports/mappings"))
                .andExpect(status().isOk());
    }

    @Test
    void upload_returnsUploadResult() throws Exception {
        var versionId = UUID.randomUUID();
        when(peoplePayrollService.uploadSnapshotFile(eq(versionId), eq(ImportType.ZOHO_PEOPLE),
                any(), any(), anyString()))
                .thenReturn(new PeoplePayrollService.SnapshotUploadResult(
                        UUID.randomUUID(), versionId, 2, List.of("Extra"), List.of(), List.of(),
                        PeriodStatus.OPEN));

        MockMultipartFile file = new MockMultipartFile(
                "file", "people.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/people/imports/{id}/upload", versionId)
                        .file(file)
                        .param("import_type", "ZOHO_PEOPLE")
                        .param("mapping_id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsImported").value(2))
                .andExpect(jsonPath("$.unmappedColumns[0]").value("Extra"));
    }

    @Test
    void getMapping_returns204WhenNoTemplate() throws Exception {
        when(peoplePayrollService.findActiveMapping(ImportType.ZOHO_PAYROLL))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/people/imports/mappings/ZOHO_PAYROLL"))
                .andExpect(status().isNoContent());
    }

    @Test
    void parseHeaders_returnsHeadersAndRowCount() throws Exception {
        when(excelSnapshotParser.parseHeaders(any()))
                .thenReturn(new ExcelSnapshotParser.ParseHeadersResult(
                        List.of("Employee ID", "Full Name"), 42));

        MockMultipartFile file = new MockMultipartFile(
                "file", "people.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/people/imports/parse-headers").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers[0]").value("Employee ID"))
                .andExpect(jsonPath("$.rowCount").value(42));
    }

    @Test
    void getSnapshotDetail_returnsPeopleRows() throws Exception {
        var versionId = UUID.randomUUID();
        var detail = new SnapshotDetailResponse(
                versionId,
                3,
                2026,
                1,
                ImportType.ZOHO_PEOPLE,
                new SnapshotUploadMetadataResponse(
                        UUID.randomUUID(),
                        ImportType.ZOHO_PEOPLE,
                        "admin@cognologix.com",
                        java.time.Instant.parse("2026-03-01T10:00:00Z"),
                        "people.xlsx",
                        2,
                        List.of("Extra"),
                        List.of(),
                        List.of()),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(peoplePayrollService.getSnapshotDetail(versionId, ImportType.ZOHO_PEOPLE))
                .thenReturn(detail);

        mockMvc.perform(get("/api/people/imports/{id}/snapshots/ZOHO_PEOPLE", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodMonth").value(3))
                .andExpect(jsonPath("$.importType").value("ZOHO_PEOPLE"))
                .andExpect(jsonPath("$.upload.originalFilename").value("people.xlsx"))
                .andExpect(jsonPath("$.upload.unmappedColumns[0]").value("Extra"));
    }

    @Test
    void getSnapshotDetail_returns404WhenNoUpload() throws Exception {
        var versionId = UUID.randomUUID();
        when(peoplePayrollService.getSnapshotDetail(versionId, ImportType.ZOHO_PAYROLL))
                .thenThrow(new NotFoundException("No snapshot upload"));

        mockMvc.perform(get("/api/people/imports/{id}/snapshots/ZOHO_PAYROLL", versionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_returnsOk() throws Exception {
        var versionId = UUID.randomUUID();
        when(peoplePayrollService.previewImport(versionId))
                .thenReturn(new PeoplePayrollService.ImportPreview(
                        List.of(), List.of(), List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/people/imports/{id}/preview", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peopleRows").isArray());
    }
}
