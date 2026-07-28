package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ClassificationConfigType;
import com.cognologix.fpa.people.dto.ClassificationConfigResponse;
import com.cognologix.fpa.people.dto.CreateClassificationConfigRequest;
import com.cognologix.fpa.people.dto.SimpleImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.cognologix.fpa.general.AdminOnly;

@RestController
@RequestMapping("/api/people/config")
@RequiredArgsConstructor
@Tag(name = "People — Classification Config", description = "Delivery PU / Management BU / Leadership BU lists")
public class ClassificationConfigController {

    private final PeoplePayrollService peoplePayrollService;

    @GetMapping("/classification")
    @Operation(summary = "List all classification config entries grouped by config_type")
    public Map<ClassificationConfigType, List<ClassificationConfigResponse>> list() {
        return peoplePayrollService.findAllClassificationConfig().stream()
                .map(ClassificationConfigResponse::from)
                .collect(Collectors.groupingBy(
                        ClassificationConfigResponse::configType,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @AdminOnly
    @PostMapping("/classification")
    @Operation(summary = "Add a value to a config_type. Returns 409 if duplicate.")
    public ResponseEntity<ClassificationConfigResponse> add(
            @Valid @RequestBody CreateClassificationConfigRequest req) {
        var saved = peoplePayrollService.addClassificationConfig(req.configType(), req.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassificationConfigResponse.from(saved));
    }

    @AdminOnly
    @DeleteMapping("/classification/{id}")
    @Operation(summary = "Remove a classification config entry. Fails if it would leave the type empty.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        peoplePayrollService.deleteClassificationConfig(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/classification/export")
    @Operation(summary = "Export all classification config entries as Excel")
    public ResponseEntity<byte[]> exportClassification() {
        return excelAttachment(
                peoplePayrollService.exportClassificationConfig(),
                "classification_config_export.xlsx");
    }

    @GetMapping("/classification/import/sample")
    @Operation(summary = "Download classification config import template (headers only)")
    public ResponseEntity<byte[]> downloadClassificationImportSample() {
        return excelAttachment(
                peoplePayrollService.buildClassificationImportSample(),
                "classification_config_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/classification/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import classification config entries — adds new values, skips duplicates")
    public SimpleImportResponse importClassification(@RequestPart("file") MultipartFile file) {
        return peoplePayrollService.importClassificationConfig(file);
    }

    private static ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
