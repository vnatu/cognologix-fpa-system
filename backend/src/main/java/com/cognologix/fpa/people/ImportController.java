package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/people/imports")
@RequiredArgsConstructor
@Tag(name = "People — Imports", description = "Column mapping templates and snapshot uploads")
public class ImportController {

    private final PeoplePayrollService peoplePayrollService;
    private final ExcelSnapshotParser excelSnapshotParser;

    @GetMapping("/mappings")
    @Operation(summary = "List active column mapping templates grouped by import_type")
    public Map<ImportType, List<MappingTemplateResponse>> listMappings() {
        return peoplePayrollService.findActiveMappings().stream()
                .map(MappingTemplateResponse::from)
                .collect(Collectors.groupingBy(
                        MappingTemplateResponse::importType,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @GetMapping("/mappings/{importType}")
    @Operation(summary = "Get the active template for an import type — 204 when none configured")
    public ResponseEntity<MappingTemplateResponse> getMapping(@PathVariable ImportType importType) {
        return peoplePayrollService.findActiveMapping(importType)
                .map(m -> ResponseEntity.ok(MappingTemplateResponse.from(m)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/mappings")
    @Operation(summary = "Create or replace the active template for an import type")
    public ResponseEntity<MappingTemplateResponse> createMapping(
            @Valid @RequestBody CreateMappingRequest req) {
        var lines = req.lines().stream()
                .map(l -> new PeoplePayrollService.MappingLineInput(l.excelColumnName(), l.systemAttribute()))
                .toList();
        var saved = peoplePayrollService.saveMappingTemplate(req.importType(), req.templateName(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(MappingTemplateResponse.from(saved));
    }

    @PostMapping(value = "/parse-headers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse Excel column headers and row count without persisting")
    public ParseHeadersResponse parseHeaders(@RequestPart("file") MultipartFile file) {
        return ParseHeadersResponse.from(excelSnapshotParser.parseHeaders(file));
    }

    @PostMapping(value = "/{periodVersionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a snapshot Excel file for a period version")
    public SnapshotUploadResponse upload(
            @PathVariable UUID periodVersionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam("import_type") ImportType importType,
            @RequestParam("mapping_id") UUID mappingId,
            Authentication auth) {
        String uploadedBy = auth != null ? auth.getName() : "system";
        var result = peoplePayrollService.uploadSnapshotFile(
                periodVersionId, importType, mappingId, file, uploadedBy);
        return SnapshotUploadResponse.from(result);
    }

    @GetMapping("/{periodVersionId}/preview")
    @Operation(summary = "Preview imported snapshot rows (first 10 of each type) plus column warnings")
    public ImportPreviewResponse preview(@PathVariable UUID periodVersionId) {
        return ImportPreviewResponse.from(peoplePayrollService.previewImport(periodVersionId));
    }

    @GetMapping("/{periodVersionId}/snapshots/{importType}")
    @Operation(summary = "All snapshot rows and upload metadata for a period version and import type")
    public SnapshotDetailResponse getSnapshotDetail(
            @PathVariable UUID periodVersionId,
            @PathVariable ImportType importType) {
        return peoplePayrollService.getSnapshotDetail(periodVersionId, importType);
    }
}
