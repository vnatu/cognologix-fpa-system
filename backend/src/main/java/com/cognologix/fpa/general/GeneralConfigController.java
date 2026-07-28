package com.cognologix.fpa.general;

import com.cognologix.fpa.general.dto.*;
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

import java.util.List;
import com.cognologix.fpa.general.AdminOnly;

@RestController
@RequestMapping("/api/general")
@RequiredArgsConstructor
@Tag(name = "General Configuration", description = "FX rates and system-wide thresholds (Settings > General tab)")
public class GeneralConfigController {

    private final GeneralConfigService generalConfigService;

    // ── FX Rates ─────────────────────────────────────────────────────────────

    @GetMapping("/fx-rates")
    @Operation(summary = "List all FX rates (newest first)")
    public List<FxRateResponse> listFxRates() {
        return generalConfigService.findAllFxRates().stream()
                .map(FxRateResponse::from)
                .toList();
    }

    @AdminOnly
    @PostMapping("/fx-rates")
    @Operation(summary = "Create a new FX rate. Automatically closes the current active rate for the same pair.")
    public ResponseEntity<FxRateResponse> createFxRate(
            @Valid @RequestBody CreateFxRateRequest req,
            Authentication auth) {
        generalConfigService.findActiveRate(req.currencyPair())
                .ifPresent(existing -> generalConfigService.closeFxRate(
                        existing, req.effectiveFrom().minusDays(1)));
        String createdBy = auth != null ? auth.getName() : "system";
        var fx = generalConfigService.createFxRate(
                req.currencyPair(), req.rate(), req.effectiveFrom(), createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(FxRateResponse.from(fx));
    }

    @GetMapping("/fx-rates/export")
    @Operation(summary = "Export all FX rates as Excel")
    public ResponseEntity<byte[]> exportFxRates() {
        return excelAttachment(generalConfigService.exportFxRates(), "fx_rates_export.xlsx");
    }

    @GetMapping("/fx-rates/import/sample")
    @Operation(summary = "Download FX rate import template (headers only)")
    public ResponseEntity<byte[]> downloadFxRateImportSample() {
        return excelAttachment(
                generalConfigService.buildFxRateImportTemplate(),
                "fx_rates_import_template.xlsx");
    }

    @AdminOnly
    @PostMapping(value = "/fx-rates/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import FX rates from Excel")
    public FxRateImportResponse importFxRates(
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        String createdBy = auth != null ? auth.getName() : "system";
        return generalConfigService.importFxRates(file, createdBy);
    }

    // ── Concentration Risk Config ─────────────────────────────────────────────

    @GetMapping("/concentration-risk-config")
    @Operation(summary = "Get current concentration risk thresholds")
    public ConcentrationRiskConfigResponse getConcentrationRiskConfig() {
        return ConcentrationRiskConfigResponse.from(generalConfigService.getConcentrationRiskConfig());
    }

    @AdminOnly
    @PutMapping("/concentration-risk-config")
    @Operation(summary = "Update the single-client concentration risk threshold")
    public ConcentrationRiskConfigResponse updateConcentrationRiskConfig(
            @Valid @RequestBody UpdateConcentrationRiskConfigRequest req) {
        var updated = generalConfigService.updateSingleClientThreshold(req.singleClientThresholdPct());
        return ConcentrationRiskConfigResponse.from(updated);
    }

    // ── Date format (ADR-025) ─────────────────────────────────────────────────

    @GetMapping("/config/date-format")
    @Operation(summary = "Get the configured date display format")
    public DateFormatResponse getDateFormat() {
        return new DateFormatResponse(generalConfigService.getDateFormat());
    }

    @AdminOnly
    @PutMapping("/config/date-format")
    @Operation(summary = "Update the date display format")
    public DateFormatResponse updateDateFormat(@Valid @RequestBody UpdateDateFormatRequest req) {
        return new DateFormatResponse(generalConfigService.updateDateFormat(req.format()));
    }

    private static ResponseEntity<byte[]> excelAttachment(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
