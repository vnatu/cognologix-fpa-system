package com.cognologix.fpa.revenue.dto;

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
            BigDecimal amountUsd
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
