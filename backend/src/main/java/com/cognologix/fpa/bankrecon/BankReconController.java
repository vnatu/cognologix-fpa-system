package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.dto.BankReconDtos.*;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import com.cognologix.fpa.general.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank-recon")
@RequiredArgsConstructor
@Tag(name = "Bank Reconciliation", description = "FinSync HDFC statement mapping (ADR-065)")
public class BankReconController {

    private final BankReconService bankReconService;

    @AdminOnly
    @PostMapping(value = "/runs/parse-headers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse HDFC statement header-block labels and transaction column headers")
    public ParseHeadersResponse parseHeaders(@RequestPart("file") MultipartFile file) {
        return bankReconService.parseStatementHeaders(file);
    }

    @GetMapping("/runs/mapping/sample")
    @Operation(summary = "Download a sample Excel with headers from the saved HDFC_BANK_STATEMENT template")
    public ResponseEntity<byte[]> sampleMapping() {
        byte[] bytes = bankReconService.sampleMappingWorkbook();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"hdfc-bank-statement-sample.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/column-mappings")
    @Operation(summary = "List the active HDFC_BANK_STATEMENT column mapping template")
    public Map<String, List<ColumnMappingTemplateResponse>> listColumnMappings() {
        return bankReconService.listActiveColumnMappings().stream()
                .map(ColumnMappingTemplateResponse::from)
                .collect(java.util.stream.Collectors.groupingBy(
                        ColumnMappingTemplateResponse::importType,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    @GetMapping("/column-mappings/{importType}")
    @Operation(summary = "Get the active HDFC_BANK_STATEMENT template — 204 when none configured")
    public ResponseEntity<ColumnMappingTemplateResponse> getColumnMapping(@PathVariable String importType) {
        if (!BankReconService.HDFC_IMPORT_TYPE.equals(importType)) {
            throw new IllegalArgumentException("Unsupported import type: " + importType);
        }
        return bankReconService.findActiveColumnMapping()
                .map(m -> ResponseEntity.ok(ColumnMappingTemplateResponse.from(m)))
                .orElse(ResponseEntity.noContent().build());
    }

    @AdminOnly
    @PostMapping("/column-mappings")
    @Operation(summary = "Create or replace the active HDFC_BANK_STATEMENT column mapping template")
    public ResponseEntity<ColumnMappingTemplateResponse> createColumnMapping(
            @RequestBody CreateColumnMappingRequest req) {
        if (req.importType() != null && !BankReconService.HDFC_IMPORT_TYPE.equals(req.importType())) {
            throw new IllegalArgumentException("Unsupported import type: " + req.importType());
        }
        var lines = req.lines().stream()
                .map(l -> new com.cognologix.fpa.people.PeoplePayrollService.MappingLineInput(
                        l.excelColumnName(), l.systemAttribute()))
                .toList();
        var saved = bankReconService.saveColumnMapping(req.templateName(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(ColumnMappingTemplateResponse.from(saved));
    }

    @AdminOnly
    @PostMapping(value = "/runs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload HDFC CSV/Excel statement using a column mapping template and run the pipeline")
    public RunResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mapping_id", required = false) UUID mappingId,
            Authentication auth) {
        return bankReconService.uploadStatement(file, mappingId, actor(auth));
    }

    @GetMapping("/runs")
    public PageResponse<RunResponse> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bankReconService.listRuns(page, size);
    }

    @GetMapping("/runs/{runId}")
    public RunResponse getRun(@PathVariable UUID runId) {
        return bankReconService.getRun(runId);
    }

    @AdminOnly
    @PutMapping("/runs/{runId}/transactions/{txId}")
    public TransactionResponse updateTransaction(
            @PathVariable UUID runId,
            @PathVariable UUID txId,
            @RequestBody UpdateTransactionRequest request,
            Authentication auth) {
        String voucher = request.voucherType() != null ? request.voucherType().name() : null;
        return bankReconService.updateTransactionMapping(
                runId, txId, request.ledgerName(), voucher, request.excluded(), actor(auth));
    }

    @AdminOnly
    @PostMapping("/runs/{runId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID runId,
            @RequestBody(required = false) ExportRequest request,
            Authentication auth) {
        List<UUID> ids = request == null ? List.of() : request.transactionIds();
        BankReconService.ExportFile file = bankReconService.generateExport(runId, ids, actor(auth));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.bytes());
    }

    @AdminOnly
    @PostMapping("/runs/{runId}/close")
    public RunResponse close(@PathVariable UUID runId, Authentication auth) {
        return bankReconService.closeRun(runId, actor(auth));
    }

    @AdminOnly
    @DeleteMapping("/runs/{runId}")
    @Operation(summary = "Discard a reconciliation run. Learned mappings are kept.")
    public ResponseEntity<Void> discard(@PathVariable UUID runId, Authentication auth) {
        bankReconService.discardRun(runId, actor(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ledgers")
    public PageResponse<LedgerResponse> ledgers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String voucherType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        VoucherType type = parseVoucherType(voucherType);
        return bankReconService.listLedgers(search, type, page, size);
    }

    @AdminOnly
    @PostMapping(value = "/ledgers/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportCountResponse importLedgers(@RequestPart("file") MultipartFile file) {
        return bankReconService.importLedgerMaster(file);
    }

    @AdminOnly
    @PostMapping("/ledgers")
    public ResponseEntity<LedgerResponse> addLedger(@RequestBody CreateLedgerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankReconService.addLedger(request));
    }

    @GetMapping("/mappings")
    public PageResponse<MappingResponse> mappings(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return bankReconService.listMappings(search, page, size);
    }

    @AdminOnly
    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        bankReconService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hints")
    public List<HintResponse> hints() {
        return bankReconService.listHints();
    }

    @AdminOnly
    @PostMapping("/hints")
    public ResponseEntity<HintResponse> createHint(@RequestBody HintRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bankReconService.createHint(request, actor(auth)));
    }

    @AdminOnly
    @PutMapping("/hints/{id}")
    public HintResponse updateHint(@PathVariable UUID id, @RequestBody HintRequest request) {
        return bankReconService.updateHint(id, request);
    }

    @AdminOnly
    @DeleteMapping("/hints/{id}")
    public ResponseEntity<Void> deleteHint(@PathVariable UUID id) {
        bankReconService.deleteHint(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contra-rules")
    public List<ContraRuleResponse> contraRules() {
        return bankReconService.listContraRules();
    }

    @AdminOnly
    @PostMapping("/contra-rules")
    public ResponseEntity<ContraRuleResponse> createContra(@RequestBody ContraRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankReconService.createContraRule(request));
    }

    @AdminOnly
    @DeleteMapping("/contra-rules/{id}")
    public ResponseEntity<Void> deleteContra(@PathVariable UUID id) {
        bankReconService.deleteContraRule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config/ollama")
    public OllamaConfigResponse getOllama() {
        return bankReconService.getOllamaConfig();
    }

    @AdminOnly
    @PutMapping("/config/ollama")
    public OllamaConfigResponse updateOllama(@RequestBody OllamaConfigRequest request) {
        return bankReconService.updateOllamaConfig(request);
    }

    @AdminOnly
    @PostMapping("/config/ollama/test")
    public OllamaTestResponse testOllama() {
        return bankReconService.testOllamaConnection();
    }

    @AdminOnly
    @PostMapping(value = "/migrate/learned-mappings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MigrateResponse migrateMappings(@RequestPart("file") MultipartFile file) {
        return bankReconService.migrateLearnings(file);
    }

    @AdminOnly
    @PostMapping(value = "/migrate/run-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MigrateResponse migrateHistory(@RequestPart("file") MultipartFile file) {
        return bankReconService.migrateRunHistory(file);
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    private static VoucherType parseVoucherType(String voucherType) {
        if (voucherType == null || voucherType.isBlank()) {
            return null;
        }
        try {
            return VoucherType.valueOf(voucherType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown voucher type: " + voucherType);
        }
    }
}
