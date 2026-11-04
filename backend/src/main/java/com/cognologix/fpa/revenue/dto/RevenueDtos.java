package com.cognologix.fpa.revenue.dto;

import com.cognologix.fpa.revenue.domain.MonthSource;
import com.cognologix.fpa.revenue.domain.RevenueCurrency;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RevenueDtos {

    private RevenueDtos() {}

    public record UploadResult(
            UUID uploadId,
            RevenueImportType importType,
            int periodMonth,
            int periodYear,
            int versionNumber,
            int rowsImported,
            List<String> unmappedColumns,
            List<String> missingColumns,
            List<String> unrecognizedCustomerCodes,
            List<String> duplicateNumbers
    ) {}

    /**
     * Flags collected on the review step. Saving without the required flags is rejected.
     * There is no option to keep a cross-period duplicate in both uploads.
     */
    public record ImportSaveOptions(
            boolean confirmVersionShrink,
            boolean confirmOtherMonths,
            boolean skipCrossPeriodDuplicates
    ) {
        public static ImportSaveOptions none() {
            return new ImportSaveOptions(false, false, false);
        }
    }

    public record EffectiveMonthSummary(
            int month,
            int year,
            int invoiceCount,
            BigDecimal amount,
            boolean activeUpload
    ) {}

    /** An incoming document number already stored on an ACTIVE upload for a different tag period. */
    public record CrossPeriodDuplicate(
            String documentNumber,
            UUID uploadId,
            String originalFilename,
            int periodMonth,
            int periodYear,
            int versionNumber
    ) {}

    public record RevenueImportPreview(
            int previousVersionCount,
            int thisVersionCount,
            int thisVersionCountIfSkipped,
            int tagFallbackBlankCount,
            int tagFallbackBlankCountIfSkipped,
            List<String> servicePeriodWarnings,
            List<EffectiveMonthSummary> months,
            List<EffectiveMonthSummary> monthsExcludingCrossPeriodDuplicates,
            List<EffectiveMonthSummary> otherMonths,
            List<EffectiveMonthSummary> otherMonthsExcludingCrossPeriodDuplicates,
            boolean otherMonthConfirmationRequired,
            boolean otherMonthConfirmationRequiredIfSkipped,
            boolean versionShrinkConfirmationRequired,
            boolean versionShrinkConfirmationRequiredIfSkipped,
            List<CrossPeriodDuplicate> crossPeriodDuplicates,
            List<String> withinFileDuplicateNumbers,
            List<String> unmappedColumns,
            List<String> missingColumns,
            List<String> unrecognizedCustomerCodes
    ) {}

    public record UploadSummary(
            UUID id,
            RevenueImportType importType,
            int periodMonth,
            int periodYear,
            int versionNumber,
            RevenueUploadStatus status,
            String uploadedBy,
            Instant uploadedAt,
            String originalFilename,
            int rowCount,
            List<String> unmappedColumns,
            List<String> missingColumns,
            List<String> unrecognizedCustomerCodes
    ) {}

    public record MonthlyRevenueSummary(
            String customerId,
            int periodMonth,
            int periodYear,
            BigDecimal invoiceTotal,
            BigDecimal creditNoteTotal,
            BigDecimal netRevenue,
            BigDecimal invoiceTotalInr,
            BigDecimal creditNoteTotalInr,
            BigDecimal netRevenueInr
    ) {}

    /**
     * Invoice / credit-note list row.
     *
     * @param amountUsd raw USD amount as invoiced — no conversion applied. Null for INR invoices
     *                  or when AmountUsd was not mapped on upload (ADR-061).
     */
    public record InvoiceListItem(
            UUID id,
            RevenueImportType importType,
            String documentNumber,
            String customerId,
            int periodMonth,
            int periodYear,
            LocalDate documentDate,
            String status,
            BigDecimal amount,
            BigDecimal balance,
            LocalDate dueDate,
            RevenueCurrency currency,
            String projectCode,
            BigDecimal amountInr,
            BigDecimal amountUsd,
            Integer serviceMonth,
            Integer serviceYear,
            MonthSource monthSource
    ) {}

    public record InvoiceListPage(
            List<InvoiceListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record RevenueVsPlanRow(
            String customerId,
            String customerName,
            BigDecimal plannedRevenue,
            BigDecimal actualNetRevenue,
            BigDecimal actualNetRevenueInr,
            BigDecimal variance,
            BigDecimal varianceInr,
            /** Net raw USD (invoices − credit notes) when any AmountUsd present; otherwise null. */
            BigDecimal actualAmountUsd
    ) {}

    public record InvoiceStatusBucket(
            String status,
            long count,
            BigDecimal totalAmount,
            BigDecimal totalAmountInr
    ) {}

    public record DsoRow(
            String customerId,
            String customerName,
            Double avgDaysOutstanding,
            LocalDate oldestOutstandingInvoiceDate,
            BigDecimal outstandingBalance,
            long unpaidInvoiceCount
    ) {}

    /** Month that contributed data to an aggregated dashboard response. */
    public record MonthCovered(int month, int year, String label) {}

    public record PeriodWithData(int month, int year, String label) {}

    public record DashboardResponse(
            int periodMonth,
            int periodYear,
            String granularity,
            Integer quarter,
            String periodLabel,
            List<MonthCovered> monthsCovered,
            String actualsCoverageNote,
            List<RevenueVsPlanRow> revenueVsPlan,
            List<InvoiceStatusBucket> invoiceStatusSummary,
            List<DsoRow> dso
    ) {}
}
