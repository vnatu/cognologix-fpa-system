package com.cognologix.fpa.general;

import com.cognologix.fpa.general.dto.FxRateImportResponse;
import com.cognologix.fpa.general.dto.FxRateImportRowError;
import com.cognologix.fpa.general.repository.ConcentrationRiskConfigRepository;
import com.cognologix.fpa.general.repository.GeneralConfigRepository;
import com.cognologix.fpa.general.repository.FxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.cognologix.fpa.general.BackupGridHelper.*;

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

    public static final String LAST_BACKUP_AT_KEY = "last_backup_at";
    public static final String GENERAL_CONFIG_BACKUP_FILE = "general_config.xlsx";
    public static final String FX_RATES_BACKUP_FILE = "fx_rates.xlsx";

    static final String[] GENERAL_CONFIG_HEADERS = {"config_key", "config_value"};
    static final String[] FX_RATE_HEADERS = {
            "currency_pair", "rate", "effective_from", "effective_to", "created_by"
    };

    public static final Set<String> ALLOWED_DATE_FORMATS =
            Set.of("DD MMM YYYY", "DD/MM/YYYY", "MM/DD/YYYY");

    private final FxRateRepository fxRateRepository;
    private final ConcentrationRiskConfigRepository concentrationRiskConfigRepository;
    private final GeneralConfigRepository generalConfigRepository;
    private final FxRateExcelIO fxRateExcelIO;

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

    @Transactional
    public FxRate createFxRateFull(String currencyPair, BigDecimal rate,
                                   LocalDate effectiveFrom, LocalDate effectiveTo, String createdBy) {
        var fx = FxRate.builder()
                .currencyPair(currencyPair)
                .rate(rate)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .createdBy(createdBy)
                .build();
        return fxRateRepository.save(fx);
    }

    public byte[] exportFxRates() {
        return fxRateExcelIO.exportFxRates(findAllFxRates());
    }

    public byte[] buildFxRateImportTemplate() {
        return fxRateExcelIO.buildImportTemplate();
    }

    @Transactional
    public FxRateImportResponse importFxRates(MultipartFile file, String defaultCreatedBy) {
        List<FxRateExcelIO.ParsedFxRateImportRow> parsedRows = fxRateExcelIO.parse(file);
        List<FxRate> existingRates = fxRateRepository.findAll();
        List<FxRateImportRowError> errors = new ArrayList<>();
        List<FxRateExcelIO.ValidatedFxRateImportRow> accepted = new ArrayList<>();
        int skipped = 0;

        for (FxRateExcelIO.ParsedFxRateImportRow parsedRow : parsedRows) {
            FxRateExcelIO.RowValidation validation = fxRateExcelIO.validateRow(parsedRow);
            if (!validation.isOk()) {
                errors.add(validation.error());
                continue;
            }
            FxRateExcelIO.ValidatedFxRateImportRow row = validation.validated();

            boolean duplicate = existingRates.stream().anyMatch(existing ->
                    fxRateExcelIO.isDuplicate(
                            row.currencyPair(), row.rate(), row.effectiveFrom(), row.effectiveTo(), existing))
                    || accepted.stream().anyMatch(pending ->
                    pending.currencyPair().equals(row.currencyPair())
                            && pending.rate().compareTo(row.rate()) == 0
                            && pending.effectiveFrom().equals(row.effectiveFrom())
                            && Objects.equals(pending.effectiveTo(), row.effectiveTo()));
            if (duplicate) {
                skipped++;
                continue;
            }

            boolean overlapsExisting = existingRates.stream()
                    .filter(existing -> existing.getCurrencyPair().equals(row.currencyPair()))
                    .anyMatch(existing -> fxRateExcelIO.rangesOverlap(
                            row.effectiveFrom(), row.effectiveTo(),
                            existing.getEffectiveFrom(), existing.getEffectiveTo()));
            if (overlapsExisting) {
                errors.add(new FxRateImportRowError(row.rowNumber(),
                        "Effective date range overlaps an existing rate for " + row.currencyPair()));
                continue;
            }

            boolean overlapsAccepted = accepted.stream()
                    .filter(pending -> pending.currencyPair().equals(row.currencyPair()))
                    .anyMatch(pending -> fxRateExcelIO.rangesOverlap(
                            row.effectiveFrom(), row.effectiveTo(),
                            pending.effectiveFrom(), pending.effectiveTo()));
            if (overlapsAccepted) {
                errors.add(new FxRateImportRowError(row.rowNumber(),
                        "Effective date range overlaps another row in this import for " + row.currencyPair()));
                continue;
            }

            accepted.add(row);
        }

        int created = 0;
        for (FxRateExcelIO.ValidatedFxRateImportRow row : accepted) {
            String createdBy = row.createdBy() != null ? row.createdBy() : defaultCreatedBy;
            createFxRateFull(row.currencyPair(), row.rate(), row.effectiveFrom(), row.effectiveTo(), createdBy);
            created++;
        }

        return new FxRateImportResponse(parsedRows.size(), created, skipped, errors);
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

    // ── Security / session (ADR-056) ──────────────────────────────────────────

    public int getJwtExpiryHours() {
        return parsePositiveInt(
                getConfigValue(GeneralConfig.JWT_EXPIRY_HOURS_KEY).orElse(null), 2, 1, 24);
    }

    public int getInactivityTimeoutMinutes() {
        return parsePositiveInt(
                getConfigValue(GeneralConfig.INACTIVITY_TIMEOUT_MINUTES_KEY).orElse(null), 30, 5, 120);
    }

    @Transactional
    public SecurityConfigSnapshot updateSecurityConfig(int jwtExpiryHours, int inactivityTimeoutMinutes) {
        if (jwtExpiryHours < 1 || jwtExpiryHours > 24) {
            throw new GeneralBadRequestException("jwtExpiryHours must be between 1 and 24");
        }
        if (inactivityTimeoutMinutes < 5 || inactivityTimeoutMinutes > 120) {
            throw new GeneralBadRequestException("inactivityTimeoutMinutes must be between 5 and 120");
        }
        setConfigValue(GeneralConfig.JWT_EXPIRY_HOURS_KEY, String.valueOf(jwtExpiryHours));
        setConfigValue(GeneralConfig.INACTIVITY_TIMEOUT_MINUTES_KEY, String.valueOf(inactivityTimeoutMinutes));
        return new SecurityConfigSnapshot(jwtExpiryHours, inactivityTimeoutMinutes);
    }

    public SecurityConfigSnapshot getSecurityConfig() {
        return new SecurityConfigSnapshot(getJwtExpiryHours(), getInactivityTimeoutMinutes());
    }

    public record SecurityConfigSnapshot(int jwtExpiryHours, int inactivityTimeoutMinutes) {}

    private static int parsePositiveInt(String raw, int defaultValue, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Optional<String> getConfigValue(String key) {
        return generalConfigRepository.findById(key).map(GeneralConfig::getConfigValue);
    }

    @Transactional
    public void setConfigValue(String key, String value) {
        GeneralConfig config = generalConfigRepository.findById(key)
                .orElseGet(() -> {
                    GeneralConfig c = new GeneralConfig();
                    c.setConfigKey(key);
                    return c;
                });
        config.setConfigValue(value);
        generalConfigRepository.save(config);
    }

    // ── Backup / restore (ADR-044 Tier 2) ────────────────────────────────────

    public List<BackupSheet> exportBackupSheets() {
        return List.of(exportGeneralConfigBackupSheet(), exportFxRatesBackupSheet());
    }

    public BackupSheet exportGeneralConfigBackupSheet() {
        List<String[]> rows = generalConfigRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(GeneralConfig::getConfigKey))
                .map(c -> row(c.getConfigKey(), c.getConfigValue()))
                .toList();
        return new BackupSheet(GENERAL_CONFIG_BACKUP_FILE, GENERAL_CONFIG_HEADERS, rows);
    }

    public BackupSheet exportFxRatesBackupSheet() {
        List<String[]> rows = new ArrayList<>();
        for (FxRate fx : findAllFxRates()) {
            rows.add(row(
                    fx.getCurrencyPair(),
                    fx.getRate().toPlainString(),
                    fx.getEffectiveFrom().toString(),
                    fx.getEffectiveTo() != null ? fx.getEffectiveTo().toString() : "",
                    str(fx.getCreatedBy())));
        }
        return new BackupSheet(FX_RATES_BACKUP_FILE, FX_RATE_HEADERS, rows);
    }

    @Transactional
    public void wipeForRestore() {
        generalConfigRepository.deleteAllInBatch();
        fxRateRepository.deleteAllInBatch();
    }

    @Transactional
    public Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(GENERAL_CONFIG_BACKUP_FILE,
                restoreGeneralConfig(rowsByFile.getOrDefault(GENERAL_CONFIG_BACKUP_FILE, List.of())));
        counts.put(FX_RATES_BACKUP_FILE,
                restoreFxRates(rowsByFile.getOrDefault(FX_RATES_BACKUP_FILE, List.of())));
        return counts;
    }

    private int restoreGeneralConfig(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                String key = requireCell(row, 0, "config_key");
                String value = requireCell(row, 1, "config_value");
                setConfigValue(key, value);
                count++;
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }
        return count;
    }

    private int restoreFxRates(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                String pair = requireCell(row, 0, "currency_pair");
                BigDecimal rate = parseDecimalRequired(cell(row, 1), "rate");
                LocalDate from = parseDate(requireCell(row, 2, "effective_from"), "effective_from");
                LocalDate to = parseDate(cell(row, 3), "effective_to");
                String createdBy = cell(row, 4);
                createFxRateFull(pair, rate, from, to, createdBy != null ? createdBy : "restore");
                count++;
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }
        return count;
    }
}
