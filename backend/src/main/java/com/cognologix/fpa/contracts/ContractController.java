package com.cognologix.fpa.contracts;

import com.cognologix.fpa.contracts.ContractDtos.AddVersionRequest;
import com.cognologix.fpa.contracts.ContractDtos.ContractDashboard;
import com.cognologix.fpa.contracts.ContractDtos.ContractDetail;
import com.cognologix.fpa.contracts.ContractDtos.ContractDocumentDownload;
import com.cognologix.fpa.contracts.ContractDtos.ContractNotificationConfig;
import com.cognologix.fpa.contracts.ContractDtos.ContractSummary;
import com.cognologix.fpa.contracts.ContractDtos.ContractTypeResponse;
import com.cognologix.fpa.contracts.ContractDtos.CreateContractRequest;
import com.cognologix.fpa.contracts.ContractDtos.CreateContractTypeRequest;
import com.cognologix.fpa.contracts.ContractDtos.CreateTemplateRequest;
import com.cognologix.fpa.contracts.ContractDtos.TemplateGroup;
import com.cognologix.fpa.contracts.ContractDtos.TemplateSummary;
import com.cognologix.fpa.contracts.ContractDtos.UpdateContractRequest;
import com.cognologix.fpa.contracts.ContractDtos.UpdateContractTypeRequest;
import com.cognologix.fpa.contracts.ContractDtos.UpdateVersionStatusRequest;
import com.cognologix.fpa.contracts.ContractDtos.VersionResponse;
import com.cognologix.fpa.general.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Contract repository, versions, templates, and expiry settings")
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @Operation(summary = "Paginated contract list")
    public Page<ContractSummary> list(
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) PaperType paperType,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) LocalDate expiryFrom,
            @RequestParam(required = false) LocalDate expiryTo,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return contractService.listContracts(
                typeId, paperType, status, customerId, expiryFrom, expiryTo, search, pageable);
    }

    @AdminOnly
    @PostMapping
    @Operation(summary = "Create a contract")
    public ResponseEntity<ContractDetail> create(@Valid @RequestBody CreateContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.createContract(request));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Expiry bands, recent contracts, and portfolio counts")
    public ContractDashboard dashboard() {
        return contractService.getDashboardData();
    }

    @GetMapping("/types")
    @Operation(summary = "Contract types. Active only unless includeInactive=true")
    public List<ContractTypeResponse> types(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return contractService.listTypes(includeInactive);
    }

    @AdminOnly
    @PostMapping("/types")
    @Operation(summary = "Add a contract type")
    public ResponseEntity<ContractTypeResponse> addType(@Valid @RequestBody CreateContractTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.addType(request));
    }

    @AdminOnly
    @PutMapping("/types/{id}")
    @Operation(summary = "Update a contract type")
    public ContractTypeResponse updateType(
            @PathVariable UUID id, @Valid @RequestBody UpdateContractTypeRequest request) {
        return contractService.updateType(id, request);
    }

    @GetMapping("/templates")
    @Operation(summary = "Templates grouped by contract type")
    public List<TemplateGroup> templates() {
        return contractService.listTemplates();
    }

    @AdminOnly
    @PostMapping(value = "/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a contract template")
    public ResponseEntity<TemplateSummary> createTemplate(
            @RequestPart("metadata") @Valid CreateTemplateRequest metadata,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.createTemplate(metadata, file));
    }

    @GetMapping("/templates/{id}/download")
    @Operation(summary = "Download the latest template document")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID id) {
        return attachment(contractService.downloadLatestTemplate(id));
    }

    @GetMapping("/config")
    @Operation(summary = "Notification reminder days and recipients")
    public ContractNotificationConfig getConfig() {
        return contractService.getNotificationConfig();
    }

    @AdminOnly
    @PutMapping("/config")
    @Operation(summary = "Update notification reminder days and recipients")
    public ContractNotificationConfig updateConfig(
            @Valid @RequestBody ContractDtos.ContractNotificationConfigRequest request) {
        return contractService.updateNotificationConfig(request.reminderDays(), request.recipients());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Contract detail with versions and notification history")
    public ContractDetail get(@PathVariable UUID id) {
        return contractService.getContract(id);
    }

    @AdminOnly
    @PutMapping("/{id}")
    @Operation(summary = "Update contract metadata")
    public ContractDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateContractRequest request) {
        return contractService.updateContract(id, request);
    }

    @AdminOnly
    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new version. The current non-superseded version is marked SUPERSEDED")
    public ResponseEntity<VersionResponse> addVersion(
            @PathVariable UUID id,
            @RequestPart("metadata") @Valid AddVersionRequest metadata,
            @RequestPart("primaryFile") MultipartFile primaryFile,
            @RequestPart(value = "supportingFiles", required = false) List<MultipartFile> supportingFiles) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.addVersion(id, metadata, primaryFile, supportingFiles));
    }

    @GetMapping("/{id}/versions/{versionId}/documents/{docId}/download")
    @Operation(summary = "Download a contract document")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id, @PathVariable UUID versionId, @PathVariable UUID docId) {
        contractService.assertDocumentOnContract(id, versionId, docId);
        return attachment(contractService.downloadDocument(docId));
    }

    @AdminOnly
    @PutMapping("/{id}/versions/{versionId}/status")
    @Operation(summary = "Set version status. SUPERSEDED cannot be set manually")
    public VersionResponse updateStatus(
            @PathVariable UUID id,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpdateVersionStatusRequest request) {
        contractService.assertVersionOnContract(id, versionId);
        return contractService.updateVersionStatus(versionId, request.status());
    }

    private static ResponseEntity<byte[]> attachment(ContractDocumentDownload download) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = download.filename() == null ? "document" : download.filename().replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(download.fileData());
    }
}
