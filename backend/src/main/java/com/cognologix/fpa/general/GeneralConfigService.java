package com.cognologix.fpa.general;

import com.cognologix.fpa.general.repository.ConcentrationRiskConfigRepository;
import com.cognologix.fpa.general.repository.GeneralConfigRepository;
import com.cognologix.fpa.general.repository.FxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Public API surface for the General Configuration module (ADR-017/ADR-018).
 *
 * Owns: FX rates, concentration risk thresholds, date format (ADR-025).
 * No dependency on any other bounded-context module — keeps the general module cycle-free.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeneralConfigService {

    public static final Set<String> ALLOWED_DATE_FORMATS =
            Set.of("DD MMM YYYY", "DD/MM/YYYY", "MM/DD/YYYY");

    private final FxRateRepository fxRateRepository;
    private final ConcentrationRiskConfigRepository concentrationRiskConfigRepository;
    private final GeneralConfigRepository generalConfigRepository;

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

    // ── Date format (ADR-025) ─────────────────────────────────────────────────

    public String getDateFormat() {
        return generalConfigRepository.findById(GeneralConfig.DATE_FORMAT_KEY)
                .map(GeneralConfig::getConfigValue)
                .orElseThrow(() -> new IllegalStateException(
                        "Date format config not found — check V7 migration seed"));
    }

    @Transactional
    public String updateDateFormat(String format) {
        if (!ALLOWED_DATE_FORMATS.contains(format)) {
            throw new GeneralBadRequestException(
                    "Invalid date format. Allowed: " + ALLOWED_DATE_FORMATS);
        }
        GeneralConfig config = generalConfigRepository.findById(GeneralConfig.DATE_FORMAT_KEY)
                .orElseGet(() -> {
                    GeneralConfig c = new GeneralConfig();
                    c.setConfigKey(GeneralConfig.DATE_FORMAT_KEY);
                    return c;
                });
        config.setConfigValue(format);
        return generalConfigRepository.save(config).getConfigValue();
    }
}
