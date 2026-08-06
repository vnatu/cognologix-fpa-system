package com.cognologix.fpa.revenue;

import com.cognologix.fpa.general.BackupSheet;
import com.cognologix.fpa.revenue.domain.*;
import com.cognologix.fpa.revenue.repository.RevenueCreditNoteRepository;
import com.cognologix.fpa.revenue.repository.RevenueInvoiceRepository;
import com.cognologix.fpa.revenue.repository.RevenueUploadRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.cognologix.fpa.general.BackupGridHelper.*;

/**
 * Backup/restore grid operations for Revenue (ADR-044 Tier 2).
 */
@Component
@RequiredArgsConstructor
class RevenueModuleBackup {

    static final String FILE_INVOICES = "revenue_invoices.xlsx";
    static final String FILE_CREDIT_NOTES = "revenue_credit_notes.xlsx";

    static final String RESTORE_UPLOAD_FILENAME = "backup-restore.xlsx";
    static final String RESTORE_UPLOAD_BY = "restore";

    private final RevenueUploadRepository revenueUploadRepository;
    private final RevenueInvoiceRepository revenueInvoiceRepository;
    private final RevenueCreditNoteRepository revenueCreditNoteRepository;

    List<BackupSheet> exportBackupSheets() {
        return List.of(exportInvoicesSheet(), exportCreditNotesSheet());
    }

    BackupSheet exportInvoicesSheet() {
        List<String[]> rows = new ArrayList<>();
        for (RevenueUpload upload : activeUploads(RevenueImportType.ZOHO_BOOKS_INVOICES)) {
            for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(upload.getId())) {
                rows.add(row(
                        str(inv.getPeriodMonth()), str(inv.getPeriodYear()),
                        inv.getInvoiceNumber(), inv.getCustomerId(),
                        inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : "",
                        str(inv.getStatus()),
                        inv.getAmount().toPlainString(),
                        inv.getCurrency().name(),
                        inv.getBalance() != null ? inv.getBalance().toPlainString() : "",
                        inv.getDueDate() != null ? inv.getDueDate().toString() : "",
                        inv.getAmountInr() != null ? inv.getAmountInr().toPlainString() : "",
                        str(inv.getProjectCode()),
                        inv.getAmountUsd() != null ? inv.getAmountUsd().toPlainString() : ""));
            }
        }
        return new BackupSheet(FILE_INVOICES,
                new String[]{"period_month", "period_year", "invoice_number", "customer_code",
                        "invoice_date", "status", "amount", "currency", "balance", "due_date",
                        "amount_inr", "project_code", "amount_usd"},
                rows);
    }

    BackupSheet exportCreditNotesSheet() {
        List<String[]> rows = new ArrayList<>();
        for (RevenueUpload upload : activeUploads(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES)) {
            for (RevenueCreditNote note : revenueCreditNoteRepository.findByRevenueUploadId(upload.getId())) {
                rows.add(row(
                        str(note.getPeriodMonth()), str(note.getPeriodYear()),
                        note.getCreditNoteNumber(), note.getCustomerId(),
                        note.getCreditNoteDate() != null ? note.getCreditNoteDate().toString() : "",
                        str(note.getStatus()),
                        note.getAmount().toPlainString(),
                        note.getCurrency().name(),
                        note.getAmountInr() != null ? note.getAmountInr().toPlainString() : "",
                        note.getAmountUsd() != null ? note.getAmountUsd().toPlainString() : ""));
            }
        }
        return new BackupSheet(FILE_CREDIT_NOTES,
                new String[]{"period_month", "period_year", "credit_note_number", "customer_code",
                        "credit_note_date", "status", "amount", "currency", "amount_inr", "amount_usd"},
                rows);
    }

    @Transactional
    void wipeRevenueData() {
        revenueCreditNoteRepository.deleteAllInBatch();
        revenueInvoiceRepository.deleteAllInBatch();
        revenueUploadRepository.deleteAllInBatch();
    }

    @Transactional
    Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<UploadKey, RevenueUpload> uploads = new HashMap<>();
        counts.put(FILE_INVOICES,
                restoreInvoices(rowsByFile.getOrDefault(FILE_INVOICES, List.of()), uploads));
        counts.put(FILE_CREDIT_NOTES,
                restoreCreditNotes(rowsByFile.getOrDefault(FILE_CREDIT_NOTES, List.of()), uploads));
        return counts;
    }

    private List<RevenueUpload> activeUploads(RevenueImportType importType) {
        return revenueUploadRepository.findAll().stream()
                .filter(u -> u.getImportType() == importType && u.getStatus() == RevenueUploadStatus.ACTIVE)
                .toList();
    }

    private RevenueUpload resolveUpload(Map<UploadKey, RevenueUpload> uploads,
                                        int month, int year, RevenueImportType importType) {
        UploadKey key = new UploadKey(month, year, importType);
        return uploads.computeIfAbsent(key, k -> revenueUploadRepository.save(RevenueUpload.builder()
                .importType(importType)
                .periodMonth(month)
                .periodYear(year)
                .versionNumber(1)
                .status(RevenueUploadStatus.ACTIVE)
                .uploadedBy(RESTORE_UPLOAD_BY)
                .originalFilename(RESTORE_UPLOAD_FILENAME)
                .rowCount(0)
                .build()));
    }

    private int restoreInvoices(List<String[]> rows, Map<UploadKey, RevenueUpload> uploads) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                RevenueUpload upload = resolveUpload(uploads, month, year, RevenueImportType.ZOHO_BOOKS_INVOICES);
                revenueInvoiceRepository.save(RevenueInvoice.builder()
                        .revenueUpload(upload)
                        .periodMonth(month)
                        .periodYear(year)
                        .invoiceNumber(requireCell(row, 2, "invoice_number"))
                        .customerId(requireCell(row, 3, "customer_code"))
                        .invoiceDate(parseDate(cell(row, 4), "invoice_date"))
                        .status(cell(row, 5))
                        .amount(parseDecimalRequired(cell(row, 6), "amount"))
                        .currency(parseCurrency(cell(row, 7)))
                        .balance(parseDecimal(cell(row, 8), "balance"))
                        .dueDate(parseDate(cell(row, 9), "due_date"))
                        .amountInr(parseDecimal(cell(row, 10), "amount_inr"))
                        .projectCode(cell(row, 11))
                        .amountUsd(parseDecimal(cell(row, 12), "amount_usd"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private int restoreCreditNotes(List<String[]> rows, Map<UploadKey, RevenueUpload> uploads) {
        int count = 0;
        for (String[] row : rows) {
            try {
                int month = parseIntRequired(cell(row, 0), "period_month");
                int year = parseIntRequired(cell(row, 1), "period_year");
                RevenueUpload upload = resolveUpload(uploads, month, year, RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES);
                revenueCreditNoteRepository.save(RevenueCreditNote.builder()
                        .revenueUpload(upload)
                        .periodMonth(month)
                        .periodYear(year)
                        .creditNoteNumber(requireCell(row, 2, "credit_note_number"))
                        .customerId(requireCell(row, 3, "customer_code"))
                        .creditNoteDate(parseDate(cell(row, 4), "credit_note_date"))
                        .status(cell(row, 5))
                        .amount(parseDecimalRequired(cell(row, 6), "amount"))
                        .currency(parseCurrency(cell(row, 7)))
                        .amountInr(parseDecimal(cell(row, 8), "amount_inr"))
                        .amountUsd(parseDecimal(cell(row, 9), "amount_usd"))
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip
            }
        }
        return count;
    }

    private static RevenueCurrency parseCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return RevenueCurrency.INR;
        }
        return RevenueCurrency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private record UploadKey(int month, int year, RevenueImportType importType) {}
}
