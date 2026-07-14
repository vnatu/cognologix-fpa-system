package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.ConflictResolution;
import com.cognologix.fpa.customer.domain.RateCardLine;
import com.cognologix.fpa.customer.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "Customer Master, rate cards, and project codes")
public class CustomerController {

    private final CustomerService customerService;

    // ── Customer Master ──────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List customers — external clients by default; pass includeInternal=true for internal BUs")
    public List<CustomerSummaryResponse> listCustomers(
            @RequestParam(defaultValue = "false") boolean includeInternal) {
        return customerService.findAllCustomers(includeInternal).stream()
                .map(CustomerSummaryResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Create a new customer")
    public ResponseEntity<CustomerSummaryResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest req) {
        var customer = customerService.createCustomer(
                req.customerCode(), req.customerName(), req.zohoBooksCustomerRef(),
                req.relationshipOwnerEmployeeId(), req.lifecycleStatus(), req.dsoDays());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomerSummaryResponse.from(customer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get customer detail")
    public ResponseEntity<CustomerDetailResponse> getCustomer(@PathVariable UUID id) {
        return customerService.getCustomerDetail(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update customer — name, lifecycle status, relationship owner, DSO. " +
            "No DELETE: use lifecycleStatus=CHURNED to retire a customer (historical data preserved).")
    public ResponseEntity<CustomerDetailResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest req) {
        return ResponseEntity.ok(customerService.updateCustomer(
                id, req.customerCode(), req.customerName(), req.lifecycleStatus(),
                req.relationshipOwnerEmployeeId(), req.dsoDays()));
    }

    // ── Customer Import (ADR-027) ────────────────────────────────────────────

    @GetMapping("/export")
    @Operation(summary = "Export all customers (including internal BUs) as Excel")
    public ResponseEntity<byte[]> exportCustomers() {
        return excelAttachment(customerService.exportCustomers(), "customers_export.xlsx");
    }

    @PostMapping(value = "/import/conflicts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Pre-flight check — which Customer Codes in the file already exist")
    public CustomerImportConflictsResponse detectImportConflicts(@RequestPart("file") MultipartFile file) {
        return customerService.detectImportConflicts(file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import customers from Excel with SKIP or REPLACE conflict resolution")
    public CustomerImportResponse importCustomers(
            @RequestPart("file") MultipartFile file,
            @RequestParam ConflictResolution conflictResolution) {
        return customerService.importCustomers(file, conflictResolution);
    }

    // ── Rate Card Import ─────────────────────────────────────────────────────

    @GetMapping("/rate-cards/export")
    @Operation(summary = "Export all rate cards (including historical) as Excel")
    public ResponseEntity<byte[]> exportRateCards() {
        return excelAttachment(customerService.exportRateCards(), "rate_cards_export.xlsx");
    }

    @GetMapping("/rate-cards/import/sample")
    @Operation(summary = "Download rate card import template (headers only)")
    public ResponseEntity<byte[]> downloadRateCardImportSample() {
        byte[] content = customerService.buildRateCardImportSample();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"rate_card_import_template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/rate-cards/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import rate cards from Excel — one row per rate card line")
    public RateCardImportResponse importRateCards(@RequestPart("file") MultipartFile file) {
        return customerService.importRateCards(file);
    }

    // ── Rate Cards ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/rate-cards")
    @Operation(summary = "List all rate cards for a customer (newest first)")
    public ResponseEntity<List<RateCardResponse>> listRateCards(@PathVariable UUID id) {
        var cards = customerService.findRateCardsByCustomer(id).stream()
                .map(RateCardResponse::from)
                .toList();
        return ResponseEntity.ok(cards);
    }

    @PostMapping("/{id}/rate-cards")
    @Operation(summary = "Create a new rate card with name, type, currency, and effective date. " +
            "Currency applies to all lines in the card (ADR-020). " +
            "Automatically closes the current active rate card. " +
            "No PUT — effective-dated model: supersede by creating a new card (spec §6).")
    public ResponseEntity<RateCardResponse> createRateCard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRateCardRequest req) {
        var lines = req.lines().stream()
                .map(l -> RateCardLine.builder()
                        .jobLevel(l.jobLevel())
                        .rateAmount(l.rateAmount())
                        .build())
                .toList();
        var card = customerService.createRateCard(
                id, req.name(), req.rateCardType(), req.currency(), req.effectiveFrom(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(RateCardResponse.from(card));
    }

    // ── Project Codes (bulk) ─────────────────────────────────────────────────

    @GetMapping("/project-codes/export")
    @Operation(summary = "Export all project codes as Excel")
    public ResponseEntity<byte[]> exportProjectCodes() {
        return excelAttachment(customerService.exportProjectCodes(), "project_codes_export.xlsx");
    }

    @GetMapping("/project-codes/import/sample")
    @Operation(summary = "Download project code import template (headers only)")
    public ResponseEntity<byte[]> downloadProjectCodeImportSample() {
        return excelAttachment(
                customerService.buildProjectCodeImportSample(),
                "project_codes_import_template.xlsx");
    }

    @PostMapping(value = "/project-codes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import project codes from Excel — skips existing codes per customer")
    public ProjectCodeImportResponse importProjectCodes(@RequestPart("file") MultipartFile file) {
        return customerService.importProjectCodes(file);
    }

    // ── Project Codes (per customer) ─────────────────────────────────────────

    @GetMapping("/{id}/project-codes")
    @Operation(summary = "List project codes for a customer")
    public ResponseEntity<List<ProjectCodeResponse>> listProjectCodes(@PathVariable UUID id) {
        var codes = customerService.findProjectCodesByCustomer(id).stream()
                .map(ProjectCodeResponse::from)
                .toList();
        return ResponseEntity.ok(codes);
    }

    @PostMapping("/{id}/project-codes")
    @Operation(summary = "Add a project code to a customer")
    public ResponseEntity<ProjectCodeResponse> addProjectCode(
            @PathVariable UUID id,
            @Valid @RequestBody AddProjectCodeRequest req) {
        var pc = customerService.addProjectCode(id, req.projectCode(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectCodeResponse.from(pc));
    }

    @DeleteMapping("/{id}/project-codes/{codeId}")
    @Operation(summary = "Remove a project code from a customer")
    public ResponseEntity<Void> removeProjectCode(
            @PathVariable UUID id,
            @PathVariable UUID codeId) {
        customerService.removeProjectCode(id, codeId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
