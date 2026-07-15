package com.cognologix.fpa.revenue;

import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.dto.RevenueDtos.*;
import com.cognologix.fpa.revenue.dto.RevenueMappingDtos.*;
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
@RequestMapping("/api/revenue")
@RequiredArgsConstructor
@Tag(name = "Revenue", description = "Zoho Books invoice/credit-note imports and revenue summaries")
public class RevenueController {

    private final RevenueService revenueService;

    // ── Imports / mappings ───────────────────────────────────────────────────

    @PostMapping(value = "/imports/invoices/parse-headers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse Excel column headers and row count without persisting")
    public ParseHeadersResponse parseHeaders(@RequestPart("file") MultipartFile file) {
        var result = revenueService.parseHeaders(file);
        return new ParseHeadersResponse(result.headers(), result.rowCount());
    }

    @GetMapping("/imports/mappings/{importType}")
    @Operation(summary = "Get the active template for a revenue import type — 204 when none configured")
    public ResponseEntity<MappingTemplateResponse> getMapping(@PathVariable RevenueImportType importType) {
        return revenueService.findActiveMapping(importType)
                .map(m -> ResponseEntity.ok(MappingTemplateResponse.from(m)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/imports/mappings")
    @Operation(summary = "Create or replace the active template for a revenue import type")
    public ResponseEntity<MappingTemplateResponse> createMapping(
            @Valid @RequestBody CreateMappingRequest req) {
        var lines = req.lines().stream()
                .map(l -> new PeoplePayrollService.MappingLineInput(l.excelColumnName(), l.systemAttribute()))
                .toList();
        var saved = revenueService.saveMappingTemplate(req.importType(), req.templateName(), lines);
        return ResponseEntity.status(HttpStatus.CREATED).body(MappingTemplateResponse.from(saved));
    }

    @GetMapping("/imports/mappings")
    @Operation(summary = "List active column mapping templates for both revenue import types")
    public Map<String, List<MappingTemplateResponse>> listMappings() {
        return revenueService.listActiveMappings().stream()
                .map(MappingTemplateResponse::from)
                .collect(Collectors.groupingBy(
                        MappingTemplateResponse::importType,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @PostMapping(value = "/imports/{periodMonth}/{periodYear}/invoices",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Zoho Books invoices for a period")
    public UploadResult uploadInvoices(
            @PathVariable int periodMonth,
            @PathVariable int periodYear,
            @RequestPart("file") MultipartFile file,
            @RequestParam("mapping_id") UUID mappingId,
            Authentication auth) {
        String uploadedBy = auth != null ? auth.getName() : "system";
        return revenueService.uploadInvoices(periodMonth, periodYear, file, mappingId, uploadedBy);
    }

    @PostMapping(value = "/imports/{periodMonth}/{periodYear}/credit-notes",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Zoho Books credit notes for a period")
    public UploadResult uploadCreditNotes(
            @PathVariable int periodMonth,
            @PathVariable int periodYear,
            @RequestPart("file") MultipartFile file,
            @RequestParam("mapping_id") UUID mappingId,
            Authentication auth) {
        String uploadedBy = auth != null ? auth.getName() : "system";
        return revenueService.uploadCreditNotes(periodMonth, periodYear, file, mappingId, uploadedBy);
    }

    @GetMapping("/imports/{periodMonth}/{periodYear}/uploads")
    @Operation(summary = "List uploads for a period (both import types, including SUPERSEDED history)")
    public List<UploadSummary> listUploads(
            @PathVariable int periodMonth,
            @PathVariable int periodYear) {
        return revenueService.listUploadsForPeriod(periodMonth, periodYear);
    }

    // ── Invoice list / summaries / dashboard ─────────────────────────────────

    @GetMapping("/invoices")
    @Operation(summary = "Paginated invoice and credit-note list")
    public InvoiceListPage getInvoices(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) Integer periodMonth,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) RevenueImportType importType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return revenueService.getInvoiceList(
                customerId, periodMonth, periodYear, status, importType, page, size);
    }

    @GetMapping("/summary/{periodMonth}/{periodYear}")
    @Operation(summary = "Net revenue summary per client for a period")
    public List<MonthlyRevenueSummary> getAllClientsSummary(
            @PathVariable int periodMonth,
            @PathVariable int periodYear) {
        return revenueService.getAllClientsMonthlyRevenue(periodMonth, periodYear);
    }

    @GetMapping("/summary/{customerId}/{periodMonth}/{periodYear}")
    @Operation(summary = "Net revenue for one client and period")
    public MonthlyRevenueSummary getClientSummary(
            @PathVariable String customerId,
            @PathVariable int periodMonth,
            @PathVariable int periodYear) {
        return revenueService.getMonthlyRevenueSummary(customerId, periodMonth, periodYear);
    }

    @GetMapping("/dashboard/{periodMonth}/{periodYear}")
    @Operation(summary = "Revenue dashboard: vs plan, invoice status, DSO")
    public DashboardResponse getDashboard(
            @PathVariable int periodMonth,
            @PathVariable int periodYear) {
        return revenueService.getDashboard(periodMonth, periodYear);
    }
}
