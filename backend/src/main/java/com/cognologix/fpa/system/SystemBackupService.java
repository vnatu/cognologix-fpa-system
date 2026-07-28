package com.cognologix.fpa.system;

import com.cognologix.fpa.budgeting.BudgetingService;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.general.GeneralBadRequestException;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.general.UserService;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.revenue.RevenueService;
import com.cognologix.fpa.system.dto.RestoreConfirmResponse;
import com.cognologix.fpa.system.dto.RestoreDryRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full system backup/restore orchestration (ADR-044 Tier 2).
 * Calls module public services only — never cross-module repositories.
 */
@Service
@RequiredArgsConstructor
public class SystemBackupService {

    private static final int RESTORE_TOKEN_TTL_MINUTES = 30;

    private final UserService userService;
    private final GeneralConfigService generalConfigService;
    private final CustomerService customerService;
    private final PeoplePayrollService peoplePayrollService;
    private final BudgetingService budgetingService;
    private final RevenueService revenueService;

    private final ConcurrentHashMap<String, PendingRestore> pendingRestores = new ConcurrentHashMap<>();

    public record BackupDownload(byte[] content, String filename) {}

    private record PendingRestore(
            String token,
            Instant expiresAt,
            Map<String, List<String[]>> rowsByFile,
            Map<String, Integer> recordCounts,
            String createdBy
    ) {}

    public BackupDownload createBackup() {
        List<BackupSheet> sheets = collectAllSheets();
        Map<String, BackupSheet> byName = new LinkedHashMap<>();
        for (BackupSheet sheet : sheets) {
            byName.put(sheet.fileName(), sheet);
        }
        List<BackupSheet> ordered = new ArrayList<>();
        for (String name : BackupManifest.EXPECTED_FILES) {
            ordered.add(byName.getOrDefault(name, emptySheet(name)));
        }
        byte[] zip = BackupZipIO.buildZip(ordered);
        String filename = "cognologix_backup_" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".zip";
        generalConfigService.setConfigValue(
                GeneralConfigService.LAST_BACKUP_AT_KEY, Instant.now().toString());
        return new BackupDownload(zip, filename);
    }

    public RestoreDryRunResponse prepareRestore(MultipartFile file, String actorEmail) {
        if (file == null || file.isEmpty()) {
            throw new GeneralBadRequestException("ZIP file is required");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".zip")) {
            throw new GeneralBadRequestException("Backup must be a .zip file");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new GeneralBadRequestException("Failed to read uploaded ZIP: " + e.getMessage());
        }

        Map<String, List<String[]>> rowsByFile = BackupZipIO.readZip(bytes);
        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String expected : BackupManifest.EXPECTED_FILES) {
            List<String[]> rows = rowsByFile.get(expected);
            if (rows == null) {
                missing.add(expected);
                counts.put(labelFor(expected), 0);
            } else {
                present.add(expected);
                counts.put(labelFor(expected), rows.size());
            }
        }

        String token = UUID.randomUUID().toString();
        Instant expires = Instant.now().plus(RESTORE_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);
        pendingRestores.put(token, new PendingRestore(token, expires, rowsByFile, counts, actorEmail));
        purgeExpiredTokens();

        String warning = "This backup contains "
                + summarize(counts)
                + ". Restoring will permanently delete all existing data and replace it with the backup contents. This cannot be undone.";

        return new RestoreDryRunResponse(token, expires, warning, present, missing, counts);
    }

    @Transactional
    public RestoreConfirmResponse confirmRestore(String token, String actorEmail) {
        PendingRestore pending = pendingRestores.remove(token);
        if (pending == null) {
            throw new GeneralBadRequestException("Invalid or expired restore token. Upload the backup again.");
        }
        if (Instant.now().isAfter(pending.expiresAt())) {
            throw new GeneralBadRequestException("Restore token expired. Upload the backup again.");
        }
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new GeneralBadRequestException("Authenticated admin is required to restore");
        }

        List<String> errors = new ArrayList<>();
        Map<String, Integer> restored = new LinkedHashMap<>();

        try {
            // Truncation order (reverse dependency)
            revenueService.wipeForRestore();
            budgetingService.wipeForRestore();
            peoplePayrollService.wipeForRestore();
            customerService.wipeForRestore();
            generalConfigService.wipeForRestore();
            userService.wipeUsersExcept(actorEmail);

            // Forward insert order
            Map<String, List<String[]>> data = pending.rowsByFile();

            int users = userService.restoreUsers(
                    data.getOrDefault("users.xlsx", List.of()),
                    BackupManifest.TEMP_RESTORE_PASSWORD,
                    actorEmail);
            restored.put("users", users);

            mergeCounts(restored, generalConfigService.restoreBackupSheets(filterFor(
                    data, "general_config.xlsx", "fx_rates.xlsx")), errors);

            mergeCounts(restored, customerService.restoreBackupSheets(filterFor(
                    data, "customers.xlsx", "rate_cards.xlsx", "project_codes.xlsx")), errors);

            mergeCounts(restored, peoplePayrollService.restoreBackupSheets(filterFor(
                    data,
                    "classification_config.xlsx",
                    "column_mapping_templates.xlsx",
                    "periods.xlsx",
                    "period_versions.xlsx",
                    "employee_registry.xlsx",
                    "alternate_id_links.xlsx",
                    "zoho_people_snapshots.xlsx",
                    "zoho_payroll_snapshots.xlsx",
                    "master_records.xlsx")), errors);

            mergeCounts(restored, budgetingService.restoreBackupSheets(filterFor(
                    data,
                    "overhead_line_items.xlsx",
                    "financial_year_plans.xlsx",
                    "forecast_types.xlsx",
                    "forecast_versions.xlsx",
                    "hc_plan.xlsx",
                    "salary_budget.xlsx",
                    "client_revenue_plan.xlsx",
                    "overhead_budget.xlsx",
                    "period_actuals.xlsx",
                    "overhead_actuals.xlsx")), errors);

            mergeCounts(restored, revenueService.restoreBackupSheets(filterFor(
                    data, "revenue_invoices.xlsx", "revenue_credit_notes.xlsx")), errors);

            generalConfigService.setConfigValue(
                    GeneralConfigService.LAST_BACKUP_AT_KEY, Instant.now().toString());

            return new RestoreConfirmResponse(
                    restored,
                    errors,
                    "Restore completed. Restored users (other than your account) must log in with temporary password "
                            + BackupManifest.TEMP_RESTORE_PASSWORD
                            + " and change it.");
        } catch (RuntimeException ex) {
            throw new GeneralBadRequestException("Restore failed: " + ex.getMessage());
        }
    }

    public String lastBackupAt() {
        return generalConfigService.getConfigValue(GeneralConfigService.LAST_BACKUP_AT_KEY).orElse(null);
    }

    private List<BackupSheet> collectAllSheets() {
        List<BackupSheet> sheets = new ArrayList<>();
        sheets.add(userService.exportUsersBackupSheet());
        sheets.addAll(generalConfigService.exportBackupSheets());
        sheets.addAll(customerService.exportBackupSheets());
        sheets.addAll(peoplePayrollService.exportBackupSheets());
        sheets.addAll(budgetingService.exportBackupSheets());
        sheets.addAll(revenueService.exportBackupSheets());
        return sheets;
    }

    private static BackupSheet emptySheet(String fileName) {
        return new BackupSheet(fileName, new String[]{"(empty)"}, List.of());
    }

    private static Map<String, List<String[]>> filterFor(Map<String, List<String[]>> all, String... names) {
        Map<String, List<String[]>> filtered = new LinkedHashMap<>();
        for (String name : names) {
            if (all.containsKey(name)) {
                filtered.put(name, all.get(name));
            }
        }
        return filtered;
    }

    private static void mergeCounts(Map<String, Integer> target, Map<String, Integer> source, List<String> errors) {
        if (source == null) {
            return;
        }
        source.forEach((file, count) -> target.merge(labelFor(file), count == null ? 0 : count, Integer::sum));
    }

    private static String labelFor(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        return fileName.endsWith(".xlsx") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static String summarize(Map<String, Integer> counts) {
        int customers = counts.getOrDefault("customers", 0);
        int employees = counts.getOrDefault("employee_registry", 0);
        int invoices = counts.getOrDefault("revenue_invoices", 0);
        int creditNotes = counts.getOrDefault("revenue_credit_notes", 0);
        int users = counts.getOrDefault("users", 0);
        return users + " users, "
                + customers + " customers, "
                + employees + " employees, "
                + invoices + " invoices, "
                + creditNotes + " credit notes";
    }

    private void purgeExpiredTokens() {
        Instant now = Instant.now();
        pendingRestores.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
