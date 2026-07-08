package com.cognologix.fpa.general;

import com.cognologix.fpa.general.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/fx-rates")
    @Operation(summary = "Create a new FX rate. Automatically closes the current active rate for the same pair.")
    public ResponseEntity<FxRateResponse> createFxRate(@Valid @RequestBody CreateFxRateRequest req) {
        generalConfigService.findActiveRate(req.currencyPair())
                .ifPresent(existing -> generalConfigService.closeFxRate(
                        existing, req.effectiveFrom().minusDays(1)));
        var fx = generalConfigService.createFxRate(
                req.currencyPair(), req.rate(), req.effectiveFrom(), "system");
        return ResponseEntity.status(HttpStatus.CREATED).body(FxRateResponse.from(fx));
    }

    // ── Concentration Risk Config ─────────────────────────────────────────────

    @GetMapping("/concentration-risk-config")
    @Operation(summary = "Get current concentration risk thresholds")
    public ConcentrationRiskConfigResponse getConcentrationRiskConfig() {
        return ConcentrationRiskConfigResponse.from(generalConfigService.getConcentrationRiskConfig());
    }

    @PutMapping("/concentration-risk-config")
    @Operation(summary = "Update the single-client concentration risk threshold")
    public ConcentrationRiskConfigResponse updateConcentrationRiskConfig(
            @Valid @RequestBody UpdateConcentrationRiskConfigRequest req) {
        var updated = generalConfigService.updateSingleClientThreshold(req.singleClientThresholdPct());
        return ConcentrationRiskConfigResponse.from(updated);
    }
}
