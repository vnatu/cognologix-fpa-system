package com.cognologix.fpa.general;

import com.cognologix.fpa.general.repository.ConcentrationRiskConfigRepository;
import com.cognologix.fpa.general.repository.FxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Public API surface for the General Configuration module (ADR-017/ADR-018).
 *
 * Owns: FX rates, concentration risk thresholds.
 * No dependency on any other bounded-context module — keeps the general module cycle-free.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeneralConfigService {

    private final FxRateRepository fxRateRepository;
    private final ConcentrationRiskConfigRepository concentrationRiskConfigRepository;

    // ── FX Rates ─────────────────────────────────────────────────────────────

    public List<FxRate> findAllFxRates() {
        return fxRateRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "effectiveFrom"));
    }

    public Optional<FxRate> findActiveRate(String currencyPair) {
        return fxRateRepository.findByCurrencyPairAndEffectiveToIsNull(currencyPair);
    }

    public Optional<FxRate> findRateOnDate(String currencyPair, LocalDate asOf) {
        return fxRateRepository.findRateOnDate(currencyPair, asOf);
    }

    @Transactional
    public FxRate createFxRate(String currencyPair, BigDecimal rate,
                               LocalDate effectiveFrom, String createdBy) {
        var fx = FxRate.builder()
                .currencyPair(currencyPair)
                .rate(rate)
                .effectiveFrom(effectiveFrom)
                .createdBy(createdBy)
                .build();
        return fxRateRepository.save(fx);
    }

    @Transactional
    public FxRate closeFxRate(FxRate rate, LocalDate effectiveTo) {
        rate.setEffectiveTo(effectiveTo);
        return fxRateRepository.save(rate);
    }

    // ── Concentration Risk Config ─────────────────────────────────────────────

    /** Returns the singleton global config row seeded by V2 migration. */
    public ConcentrationRiskConfig getConcentrationRiskConfig() {
        return concentrationRiskConfigRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Concentration risk config not found — check V2 migration seed"));
    }

    @Transactional
    public ConcentrationRiskConfig updateSingleClientThreshold(BigDecimal thresholdPct) {
        var config = getConcentrationRiskConfig();
        config.setSingleClientThresholdPct(thresholdPct);
        return concentrationRiskConfigRepository.save(config);
    }
}
