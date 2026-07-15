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
            BigDecimal amountInr
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
            BigDecimal varianceInr
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

    public record DashboardResponse(
            int periodMonth,
            int periodYear,
            List<RevenueVsPlanRow> revenueVsPlan,
            List<InvoiceStatusBucket> invoiceStatusSummary,
            List<DsoRow> dso
    ) {}
}
