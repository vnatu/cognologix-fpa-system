package com.cognologix.fpa.revenue;

import com.cognologix.fpa.budgeting.BudgetingService;
import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.FxRate;
import com.cognologix.fpa.general.GeneralConfigService;
import com.cognologix.fpa.people.MappingTemplateApi;
import com.cognologix.fpa.people.PeoplePayrollService;
import com.cognologix.fpa.revenue.domain.*;
import com.cognologix.fpa.revenue.dto.RevenueDtos.*;
import com.cognologix.fpa.revenue.repository.RevenueCreditNoteRepository;
import com.cognologix.fpa.revenue.repository.RevenueInvoiceRepository;
import com.cognologix.fpa.revenue.repository.RevenueUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public API surface for the Revenue module (ADR-039, ADR-040).
 * Controllers and other modules call this class only — never sub-packages directly (ADR-008).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueService {

    private static final String USD_INR = "USD_INR";
    private static final Set<String> REVENUE_IMPORT_TYPE_NAMES = Set.of(
            RevenueImportType.ZOHO_BOOKS_INVOICES.name(),
            RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES.name());

    private final RevenueUploadRepository revenueUploadRepository;
    private final RevenueInvoiceRepository revenueInvoiceRepository;
    private final RevenueCreditNoteRepository revenueCreditNoteRepository;
    private final RevenueExcelParser revenueExcelParser;
    private final PeoplePayrollService peoplePayrollService;
    private final CustomerService customerService;
    private final GeneralConfigService generalConfigService;
    private final BudgetingService budgetingService;

    // ── Column mapping (shared import_column_mapping table via People public API) ──

    public List<MappingTemplateApi> listActiveMappings() {
        return peoplePayrollService.findActiveMappingApis(REVENUE_IMPORT_TYPE_NAMES);
    }

    public Optional<MappingTemplateApi> findActiveMapping(RevenueImportType importType) {
        return peoplePayrollService.findActiveMappingApi(importType.name());
    }

    @Transactional
    public MappingTemplateApi saveMappingTemplate(
            RevenueImportType importType, String templateName,
            List<PeoplePayrollService.MappingLineInput> lines) {
        return peoplePayrollService.saveMappingTemplateApi(importType.name(), templateName, lines);
    }

    public RevenueExcelParser.ParseHeadersResult parseHeaders(MultipartFile file) {
        return revenueExcelParser.parseHeaders(file);
    }

    // ── Uploads ──────────────────────────────────────────────────────────────

    @Transactional
    public UploadResult uploadInvoices(
            int periodMonth, int periodYear, MultipartFile file, UUID mappingId, String uploadedBy) {
        validatePeriod(periodMonth, periodYear);
        return upload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear, file, mappingId, uploadedBy);
    }

    @Transactional
    public UploadResult uploadCreditNotes(
            int periodMonth, int periodYear, MultipartFile file, UUID mappingId, String uploadedBy) {
        validatePeriod(periodMonth, periodYear);
        return upload(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear, file, mappingId, uploadedBy);
    }

    /**
     * Re-upload contract (ADR-033 / ADR-039): if an ACTIVE upload already exists for this
     * import_type + period, mark it SUPERSEDED and create version_number + 1.
     */
    private UploadResult upload(
            RevenueImportType importType,
            int periodMonth,
            int periodYear,
            MultipartFile file,
            UUID mappingId,
            String uploadedBy) {

        MappingTemplateApi mapping = peoplePayrollService.findMappingApiById(mappingId)
                .orElseThrow(() -> new RevenueNotFoundException("Mapping template not found: " + mappingId));
        if (!importType.name().equals(mapping.importType())) {
            throw new RevenueBadRequestException(
                    "Mapping import type " + mapping.importType() + " does not match upload type " + importType);
        }

        Map<String, String> excelToAttr = mapping.lines().stream()
                .collect(Collectors.toMap(
                        MappingTemplateApi.MappingLineApi::excelColumnName,
                        MappingTemplateApi.MappingLineApi::systemAttribute,
                        (a, b) -> a,
                        LinkedHashMap::new));

        RevenueExcelParser.ParsedWorkbook parsed = revenueExcelParser.parse(file, excelToAttr);
        List<Map<String, String>> rows = parsed.rows();

        Optional<RevenueUpload> activeOpt = revenueUploadRepository
                .findByImportTypeAndPeriodMonthAndPeriodYearAndStatus(
                        importType, periodMonth, periodYear, RevenueUploadStatus.ACTIVE);
        int nextVersion = 1;
        if (activeOpt.isPresent()) {
            RevenueUpload previous = activeOpt.get();
            previous.setStatus(RevenueUploadStatus.SUPERSEDED);
            revenueUploadRepository.save(previous);
            nextVersion = previous.getVersionNumber() + 1;
        } else {
            nextVersion = revenueUploadRepository
                    .findFirstByImportTypeAndPeriodMonthAndPeriodYearOrderByVersionNumberDesc(
                            importType, periodMonth, periodYear)
                    .map(u -> u.getVersionNumber() + 1)
                    .orElse(1);
        }

        List<String> unrecognized = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Set<String> seenNumbers = new HashSet<>();

        RevenueUpload upload = RevenueUpload.builder()
                .importType(importType)
                .periodMonth(periodMonth)
                .periodYear(periodYear)
                .versionNumber(nextVersion)
                .status(RevenueUploadStatus.ACTIVE)
                .uploadedBy(uploadedBy != null ? uploadedBy : "system")
                .originalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.xlsx")
                .rowCount(rows.size())
                .unmappedColumns(joinCsv(parsed.unmappedColumns()))
                .missingColumns(joinCsv(parsed.missingColumns()))
                .build();
        upload = revenueUploadRepository.save(upload);

        if (importType == RevenueImportType.ZOHO_BOOKS_INVOICES) {
            for (Map<String, String> row : rows) {
                String invoiceNumber = RevenueExcelParser.required(row, RevenueSystemAttribute.INVOICE_NUMBER);
                if (!seenNumbers.add(invoiceNumber) && !duplicates.contains(invoiceNumber)) {
                    duplicates.add(invoiceNumber);
                    continue;
                }
                RevenueInvoice invoice = buildInvoice(upload, periodMonth, periodYear, row, unrecognized);
                revenueInvoiceRepository.save(invoice);
            }
        } else {
            for (Map<String, String> row : rows) {
                String creditNoteNumber = RevenueExcelParser.required(row, RevenueSystemAttribute.CREDIT_NOTE_NUMBER);
                if (!seenNumbers.add(creditNoteNumber) && !duplicates.contains(creditNoteNumber)) {
                    duplicates.add(creditNoteNumber);
                    continue;
                }
                RevenueCreditNote note = buildCreditNote(upload, periodMonth, periodYear, row, unrecognized);
                revenueCreditNoteRepository.save(note);
            }
        }

        upload.setUnrecognizedCustomerCodes(joinCsv(unrecognized));
        upload.setRowCount(rows.size() - duplicates.size());
        revenueUploadRepository.save(upload);

        return new UploadResult(
                upload.getId(),
                importType,
                periodMonth,
                periodYear,
                upload.getVersionNumber(),
                upload.getRowCount(),
                parsed.unmappedColumns(),
                parsed.missingColumns(),
                List.copyOf(unrecognized),
                List.copyOf(duplicates));
    }

    private RevenueInvoice buildInvoice(
            RevenueUpload upload, int periodMonth, int periodYear,
            Map<String, String> row, List<String> unrecognized) {

        String customerCode = RevenueExcelParser.required(row, RevenueSystemAttribute.CUSTOMER_CODE);
        if (!customerService.isKnownCustomer(customerCode) && !unrecognized.contains(customerCode)) {
            unrecognized.add(customerCode);
        }

        LocalDate invoiceDate = RevenueExcelParser.requiredDate(row, RevenueSystemAttribute.INVOICE_DATE);
        BigDecimal amount = RevenueExcelParser.requiredDecimal(row, RevenueSystemAttribute.AMOUNT);
        RevenueCurrency currency = parseCurrency(RevenueExcelParser.optional(row, RevenueSystemAttribute.CURRENCY));
        FxConversion fx = convertToInr(amount, currency, invoiceDate);

        return RevenueInvoice.builder()
                .revenueUpload(upload)
                .periodMonth(periodMonth)
                .periodYear(periodYear)
                .invoiceNumber(RevenueExcelParser.required(row, RevenueSystemAttribute.INVOICE_NUMBER))
                .customerId(customerCode)
                .invoiceDate(invoiceDate)
                .status(RevenueExcelParser.required(row, RevenueSystemAttribute.STATUS))
                .amount(amount)
                .balance(RevenueExcelParser.optionalDecimal(row, RevenueSystemAttribute.BALANCE))
                .dueDate(RevenueExcelParser.optionalDate(row, RevenueSystemAttribute.DUE_DATE))
                .currency(currency)
                .projectCode(RevenueExcelParser.optional(row, RevenueSystemAttribute.PROJECT_CODE))
                .amountInr(fx.amountInr())
                .fxRateId(fx.fxRateId())
                .build();
    }

    private RevenueCreditNote buildCreditNote(
            RevenueUpload upload, int periodMonth, int periodYear,
            Map<String, String> row, List<String> unrecognized) {

        String customerCode = RevenueExcelParser.required(row, RevenueSystemAttribute.CUSTOMER_CODE);
        if (!customerService.isKnownCustomer(customerCode) && !unrecognized.contains(customerCode)) {
            unrecognized.add(customerCode);
        }

        LocalDate creditNoteDate = RevenueExcelParser.requiredDate(row, RevenueSystemAttribute.CREDIT_NOTE_DATE);
        BigDecimal amount = RevenueExcelParser.requiredDecimal(row, RevenueSystemAttribute.AMOUNT).abs();
        RevenueCurrency currency = parseCurrency(RevenueExcelParser.optional(row, RevenueSystemAttribute.CURRENCY));
        FxConversion fx = convertToInr(amount, currency, creditNoteDate);

        return RevenueCreditNote.builder()
                .revenueUpload(upload)
                .periodMonth(periodMonth)
                .periodYear(periodYear)
                .creditNoteNumber(RevenueExcelParser.required(row, RevenueSystemAttribute.CREDIT_NOTE_NUMBER))
                .customerId(customerCode)
                .creditNoteDate(creditNoteDate)
                .status(RevenueExcelParser.required(row, RevenueSystemAttribute.STATUS))
                .amount(amount)
                .currency(currency)
                .amountInr(fx.amountInr())
                .fxRateId(fx.fxRateId())
                .build();
    }

    private FxConversion convertToInr(BigDecimal amount, RevenueCurrency currency, LocalDate asOf) {
        if (currency == RevenueCurrency.INR) {
            return new FxConversion(amount, null);
        }
        FxRate rate = generalConfigService.findRateOnDate(USD_INR, asOf)
                .orElseThrow(() -> new RevenueBadRequestException(
                        "No USD_INR FX rate effective on " + asOf + " (ADR-017)"));
        BigDecimal amountInr = amount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);
        return new FxConversion(amountInr, rate.getId());
    }

    private record FxConversion(BigDecimal amountInr, UUID fxRateId) {}

    // ── Period upload history ────────────────────────────────────────────────

    public List<UploadSummary> listUploadsForPeriod(int periodMonth, int periodYear) {
        validatePeriod(periodMonth, periodYear);
        return revenueUploadRepository
                .findByPeriodMonthAndPeriodYearOrderByUploadedAtDesc(periodMonth, periodYear)
                .stream()
                .map(this::toUploadSummary)
                .toList();
    }

    private UploadSummary toUploadSummary(RevenueUpload u) {
        return new UploadSummary(
                u.getId(),
                u.getImportType(),
                u.getPeriodMonth(),
                u.getPeriodYear(),
                u.getVersionNumber(),
                u.getStatus(),
                u.getUploadedBy(),
                u.getUploadedAt(),
                u.getOriginalFilename(),
                u.getRowCount(),
                splitCsv(u.getUnmappedColumns()),
                splitCsv(u.getMissingColumns()),
                splitCsv(u.getUnrecognizedCustomerCodes()));
    }

    // ── Net revenue summaries (called by Budgeting & Forecasting) ────────────

    public MonthlyRevenueSummary getMonthlyRevenueSummary(String customerId, int periodMonth, int periodYear) {
        validatePeriod(periodMonth, periodYear);
        if (customerId == null || customerId.isBlank()) {
            throw new RevenueBadRequestException("customerId is required");
        }
        String code = customerId.trim();
        BigDecimal invoiceTotal = BigDecimal.ZERO;
        BigDecimal invoiceTotalInr = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        BigDecimal creditTotalInr = BigDecimal.ZERO;

        Optional<RevenueUpload> invoiceUpload = findActiveUpload(
                RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear);
        if (invoiceUpload.isPresent()) {
            for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(invoiceUpload.get().getId())) {
                if (code.equals(inv.getCustomerId())) {
                    invoiceTotal = invoiceTotal.add(inv.getAmount());
                    invoiceTotalInr = invoiceTotalInr.add(nullToZero(inv.getAmountInr()));
                }
            }
        }

        Optional<RevenueUpload> creditUpload = findActiveUpload(
                RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear);
        if (creditUpload.isPresent()) {
            for (RevenueCreditNote note : revenueCreditNoteRepository.findByRevenueUploadId(creditUpload.get().getId())) {
                if (code.equals(note.getCustomerId())) {
                    creditTotal = creditTotal.add(note.getAmount());
                    creditTotalInr = creditTotalInr.add(nullToZero(note.getAmountInr()));
                }
            }
        }

        return new MonthlyRevenueSummary(
                code,
                periodMonth,
                periodYear,
                invoiceTotal,
                creditTotal,
                invoiceTotal.subtract(creditTotal),
                invoiceTotalInr,
                creditTotalInr,
                invoiceTotalInr.subtract(creditTotalInr));
    }

    public List<MonthlyRevenueSummary> getAllClientsMonthlyRevenue(int periodMonth, int periodYear) {
        validatePeriod(periodMonth, periodYear);
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();

        findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear)
                .ifPresent(upload -> {
                    for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(upload.getId())) {
                        BigDecimal[] t = totals.computeIfAbsent(
                                inv.getCustomerId(),
                                k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                        t[0] = t[0].add(inv.getAmount());
                        t[1] = t[1].add(nullToZero(inv.getAmountInr()));
                    }
                });

        findActiveUpload(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear)
                .ifPresent(upload -> {
                    for (RevenueCreditNote note : revenueCreditNoteRepository.findByRevenueUploadId(upload.getId())) {
                        BigDecimal[] t = totals.computeIfAbsent(
                                note.getCustomerId(),
                                k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                        t[2] = t[2].add(note.getAmount());
                        t[3] = t[3].add(nullToZero(note.getAmountInr()));
                    }
                });

        return totals.entrySet().stream()
                .map(e -> {
                    BigDecimal[] t = e.getValue();
                    return new MonthlyRevenueSummary(
                            e.getKey(),
                            periodMonth,
                            periodYear,
                            t[0],
                            t[2],
                            t[0].subtract(t[2]),
                            t[1],
                            t[3],
                            t[1].subtract(t[3]));
                })
                .sorted(Comparator.comparing(MonthlyRevenueSummary::customerId))
                .toList();
    }

    // ── Invoice list ─────────────────────────────────────────────────────────

    public InvoiceListPage getInvoiceList(
            String customerId,
            Integer periodMonth,
            Integer periodYear,
            String status,
            RevenueImportType importType,
            int page,
            int size) {

        List<UUID> invoiceUploadIds = new ArrayList<>();
        List<UUID> creditUploadIds = new ArrayList<>();

        if (importType == null || importType == RevenueImportType.ZOHO_BOOKS_INVOICES) {
            invoiceUploadIds.addAll(findActiveUploadIds(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear));
        }
        if (importType == null || importType == RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES) {
            creditUploadIds.addAll(findActiveUploadIds(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear));
        }

        List<InvoiceListItem> all = new ArrayList<>();
        if (!invoiceUploadIds.isEmpty()) {
            for (UUID uploadId : invoiceUploadIds) {
                for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(uploadId)) {
                    if (matchesFilter(inv.getCustomerId(), inv.getPeriodMonth(), inv.getPeriodYear(),
                            inv.getStatus(), customerId, periodMonth, periodYear, status)) {
                        all.add(toInvoiceItem(inv));
                    }
                }
            }
        }
        if (!creditUploadIds.isEmpty()) {
            for (UUID uploadId : creditUploadIds) {
                for (RevenueCreditNote note : revenueCreditNoteRepository.findByRevenueUploadId(uploadId)) {
                    if (matchesFilter(note.getCustomerId(), note.getPeriodMonth(), note.getPeriodYear(),
                            note.getStatus(), customerId, periodMonth, periodYear, status)) {
                        all.add(toCreditItem(note));
                    }
                }
            }
        }

        all.sort(Comparator
                .comparing(InvoiceListItem::periodYear).reversed()
                .thenComparing(InvoiceListItem::periodMonth).reversed()
                .thenComparing(InvoiceListItem::documentNumber));

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new InvoiceListPage(all.subList(from, to), page, size, all.size(), totalPages);
    }

    private boolean matchesFilter(
            String rowCustomer, int rowMonth, int rowYear, String rowStatus,
            String customerId, Integer periodMonth, Integer periodYear, String status) {
        if (customerId != null && !customerId.isBlank() && !customerId.trim().equals(rowCustomer)) {
            return false;
        }
        if (periodMonth != null && !periodMonth.equals(rowMonth)) {
            return false;
        }
        if (periodYear != null && !periodYear.equals(rowYear)) {
            return false;
        }
        if (status != null && !status.isBlank()
                && (rowStatus == null || !rowStatus.equalsIgnoreCase(status.trim()))) {
            return false;
        }
        return true;
    }

    private InvoiceListItem toInvoiceItem(RevenueInvoice inv) {
        return new InvoiceListItem(
                inv.getId(),
                RevenueImportType.ZOHO_BOOKS_INVOICES,
                inv.getInvoiceNumber(),
                inv.getCustomerId(),
                inv.getPeriodMonth(),
                inv.getPeriodYear(),
                inv.getInvoiceDate(),
                inv.getStatus(),
                inv.getAmount(),
                inv.getBalance(),
                inv.getDueDate(),
                inv.getCurrency(),
                inv.getProjectCode(),
                inv.getAmountInr());
    }

    private InvoiceListItem toCreditItem(RevenueCreditNote note) {
        return new InvoiceListItem(
                note.getId(),
                RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES,
                note.getCreditNoteNumber(),
                note.getCustomerId(),
                note.getPeriodMonth(),
                note.getPeriodYear(),
                note.getCreditNoteDate(),
                note.getStatus(),
                note.getAmount(),
                null,
                null,
                note.getCurrency(),
                null,
                note.getAmountInr());
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    public DashboardResponse getDashboard(int periodMonth, int periodYear) {
        validatePeriod(periodMonth, periodYear);
        List<MonthlyRevenueSummary> actuals = getAllClientsMonthlyRevenue(periodMonth, periodYear);

        List<RevenueVsPlanRow> vsPlan = new ArrayList<>();
        for (MonthlyRevenueSummary actual : actuals) {
            Optional<CustomerService.BuCustomerRef> customer =
                    customerService.resolveBuCustomer(actual.customerId());
            UUID customerUuid = customer.map(CustomerService.BuCustomerRef::id).orElse(null);
            BigDecimal planned = BigDecimal.ZERO;
            if (customerUuid != null) {
                planned = budgetingService.getClientRevenuePlan(customerUuid, periodMonth, periodYear)
                        .map(BudgetingService.ClientRevenuePlanView::plannedTotal)
                        .orElse(BigDecimal.ZERO);
            }
            String name = customer.map(CustomerService.BuCustomerRef::customerName).orElse(actual.customerId());
            vsPlan.add(new RevenueVsPlanRow(
                    actual.customerId(),
                    name,
                    planned,
                    actual.netRevenue(),
                    actual.netRevenueInr(),
                    actual.netRevenue().subtract(planned),
                    actual.netRevenueInr().subtract(planned)));
        }

        Map<String, InvoiceStatusBucketAccum> statusMap = new LinkedHashMap<>();
        findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear)
                .ifPresent(upload -> {
                    for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(upload.getId())) {
                        String st = inv.getStatus() != null ? inv.getStatus() : "Unknown";
                        InvoiceStatusBucketAccum acc = statusMap.computeIfAbsent(
                                st, k -> new InvoiceStatusBucketAccum());
                        acc.count++;
                        acc.total = acc.total.add(inv.getAmount());
                        acc.totalInr = acc.totalInr.add(nullToZero(inv.getAmountInr()));
                    }
                });
        List<InvoiceStatusBucket> statusSummary = statusMap.entrySet().stream()
                .map(e -> new InvoiceStatusBucket(
                        e.getKey(), e.getValue().count, e.getValue().total, e.getValue().totalInr))
                .sorted(Comparator.comparing(InvoiceStatusBucket::status))
                .toList();

        Map<String, DsoAccum> dsoMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear)
                .ifPresent(upload -> {
                    for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(upload.getId())) {
                        if (isPaidStatus(inv.getStatus()) || isVoidStatus(inv.getStatus())) {
                            continue;
                        }
                        if (inv.getInvoiceDate() == null) {
                            continue;
                        }
                        DsoAccum acc = dsoMap.computeIfAbsent(inv.getCustomerId(), k -> new DsoAccum());
                        // Informational DSO: days from invoice date to today for unpaid invoices
                        long days = ChronoUnit.DAYS.between(inv.getInvoiceDate(), today);
                        acc.totalDays += Math.max(days, 0);
                        acc.count++;
                        acc.outstanding = acc.outstanding.add(nullToZero(inv.getBalance()));
                        if (acc.oldestInvoiceDate == null || inv.getInvoiceDate().isBefore(acc.oldestInvoiceDate)) {
                            acc.oldestInvoiceDate = inv.getInvoiceDate();
                        }
                    }
                });
        List<DsoRow> dsoRows = dsoMap.entrySet().stream()
                .map(e -> {
                    Optional<CustomerService.BuCustomerRef> customer =
                            customerService.resolveBuCustomer(e.getKey());
                    DsoAccum acc = e.getValue();
                    Double avg = acc.count == 0 ? null : (double) acc.totalDays / acc.count;
                    return new DsoRow(
                            e.getKey(),
                            customer.map(CustomerService.BuCustomerRef::customerName).orElse(e.getKey()),
                            avg,
                            acc.oldestInvoiceDate,
                            acc.outstanding,
                            acc.count);
                })
                .sorted(Comparator.comparing(DsoRow::customerId))
                .toList();

        return new DashboardResponse(periodMonth, periodYear, vsPlan, statusSummary, dsoRows);
    }

    private static class InvoiceStatusBucketAccum {
        long count;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalInr = BigDecimal.ZERO;
    }

    private static class DsoAccum {
        long totalDays;
        long count;
        BigDecimal outstanding = BigDecimal.ZERO;
        LocalDate oldestInvoiceDate;
    }

    private boolean isPaidStatus(String status) {
        return status != null && status.equalsIgnoreCase("Paid");
    }

    private boolean isVoidStatus(String status) {
        return status != null && status.equalsIgnoreCase("Void");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<RevenueUpload> findActiveUpload(
            RevenueImportType importType, int periodMonth, int periodYear) {
        return revenueUploadRepository.findByImportTypeAndPeriodMonthAndPeriodYearAndStatus(
                importType, periodMonth, periodYear, RevenueUploadStatus.ACTIVE);
    }

    private List<UUID> findActiveUploadIds(
            RevenueImportType importType, Integer periodMonth, Integer periodYear) {
        if (periodMonth != null && periodYear != null) {
            return findActiveUpload(importType, periodMonth, periodYear)
                    .map(u -> List.of(u.getId()))
                    .orElse(List.of());
        }
        return revenueUploadRepository.findAll().stream()
                .filter(u -> u.getImportType() == importType && u.getStatus() == RevenueUploadStatus.ACTIVE)
                .filter(u -> periodMonth == null || periodMonth.equals(u.getPeriodMonth()))
                .filter(u -> periodYear == null || periodYear.equals(u.getPeriodYear()))
                .map(RevenueUpload::getId)
                .toList();
    }

    private static RevenueCurrency parseCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return RevenueCurrency.USD;
        }
        try {
            return RevenueCurrency.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RevenueBadRequestException("Unsupported currency: " + raw + " (expected USD or INR)");
        }
    }

    private static void validatePeriod(int periodMonth, int periodYear) {
        if (periodMonth < 1 || periodMonth > 12) {
            throw new RevenueBadRequestException("periodMonth must be between 1 and 12");
        }
        if (periodYear < 2000 || periodYear > 2100) {
            throw new RevenueBadRequestException("periodYear is out of range");
        }
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
