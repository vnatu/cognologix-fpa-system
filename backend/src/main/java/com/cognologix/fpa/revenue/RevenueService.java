package com.cognologix.fpa.revenue;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.general.FxRate;
import com.cognologix.fpa.general.BackupSheet;
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
    private final RevenueExcelExporter revenueExcelExporter;
    private final PeoplePayrollService peoplePayrollService;
    private final CustomerService customerService;
    private final GeneralConfigService generalConfigService;
    private final RevenueModuleBackup revenueModuleBackup;

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
                .amountUsd(RevenueExcelParser.optionalUsdAmount(row, RevenueSystemAttribute.AMOUNT_USD))
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
        BigDecimal amountUsd = RevenueExcelParser.optionalUsdAmount(row, RevenueSystemAttribute.AMOUNT_USD);
        if (amountUsd != null) {
            amountUsd = amountUsd.abs();
        }

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
                .amountUsd(amountUsd)
                .fxRateId(fx.fxRateId())
                .build();
    }

    /**
     * USD→INR only. INR (and default unmapped currency) keeps amount_inr = amount with no fx_rate_id.
     */
    private FxConversion convertToInr(BigDecimal amount, RevenueCurrency currency, LocalDate asOf) {
        if (currency != RevenueCurrency.USD) {
            return new FxConversion(amount, null);
        }
        FxRate rate = generalConfigService.findRateOnDate(USD_INR, asOf)
                .orElseThrow(() -> new RevenueBadRequestException(
                        "No USD_INR FX rate effective on " + asOf
                                + ". Add an FX rate in Settings → General before importing USD amounts."));
        BigDecimal amountInr = amount.multiply(rate.getRate()).setScale(3, RoundingMode.HALF_UP);
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

    /**
     * Net revenue per client for a period.
     *
     * @return {@code null} when no active Zoho Books invoice/credit-note upload exists for the
     *         period (Budgeting falls back to {@code actual_revenue_manual}); otherwise the
     *         per-client list (may be empty if uploads exist but contain no rows).
     */
    public List<MonthlyRevenueSummary> getAllClientsMonthlyRevenue(int periodMonth, int periodYear) {
        validatePeriod(periodMonth, periodYear);
        boolean hasUpload =
                findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear).isPresent()
                || findActiveUpload(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear).isPresent();
        if (!hasUpload) {
            return null;
        }

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

        List<InvoiceListItem> all = listAllInvoiceItems(customerId, periodMonth, periodYear, status, importType);

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new InvoiceListPage(all.subList(from, to), page, size, all.size(), totalPages);
    }

    public List<InvoiceListItem> listAllInvoiceItems(
            String customerId,
            Integer periodMonth,
            Integer periodYear,
            String status,
            RevenueImportType importType) {

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
        return all;
    }

    public byte[] exportInvoices(
            String customerId,
            Integer periodMonth,
            Integer periodYear,
            String status) {
        List<InvoiceListItem> items = listAllInvoiceItems(
                customerId, periodMonth, periodYear, status, RevenueImportType.ZOHO_BOOKS_INVOICES);
        return revenueExcelExporter.exportInvoices(items);
    }

    public byte[] exportCreditNotes(
            String customerId,
            Integer periodMonth,
            Integer periodYear,
            String status) {
        List<InvoiceListItem> items = listAllInvoiceItems(
                customerId, periodMonth, periodYear, status, RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES);
        return revenueExcelExporter.exportCreditNotes(items);
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
                inv.getAmountInr(),
                inv.getAmountUsd());
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
                note.getAmountInr(),
                note.getAmountUsd());
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    /**
     * Builds the Revenue Dashboard. Planned figures are supplied by the caller so this module
     * does not depend on Budgeting (avoids a Modulith cycle with budgeting → revenue actuals).
     *
     * <p>Granularity (ADR-049 pattern applied to Revenue):
     * <ul>
     *   <li>{@code MONTHLY} — single month (path {@code periodMonth}/{@code periodYear})</li>
     *   <li>{@code QUARTERLY} — sum of 3 Indian-FY months for {@code quarter} (1=Apr–Jun … 4=Jan–Mar)</li>
     *   <li>{@code ANNUAL} — sum of FY months that have invoice/credit-note uploads</li>
     * </ul>
     */
    public DashboardResponse getDashboard(
            int periodMonth,
            int periodYear,
            String granularityRaw,
            Integer quarter,
            PlannedRevenueLookup plannedRevenueLookup) {
        validatePeriod(periodMonth, periodYear);
        String granularity = normalizeGranularity(granularityRaw);
        int resolvedQuarter = resolveQuarter(granularity, quarter, periodMonth);

        List<int[]> scopeMonths = monthsInScope(granularity, periodMonth, periodYear, resolvedQuarter);
        List<int[]> monthsWithData = scopeMonths.stream()
                .filter(my -> hasActiveUpload(my[0], my[1]))
                .toList();

        // ANNUAL aggregates only months with data; MONTHLY/QUARTERLY iterate the full scope
        List<int[]> aggregateMonths = "ANNUAL".equals(granularity) ? monthsWithData : scopeMonths;

        Map<String, VsPlanAccum> vsPlanMap = new LinkedHashMap<>();
        Map<String, InvoiceStatusBucketAccum> statusMap = new LinkedHashMap<>();
        Map<String, DsoAccum> dsoMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        for (int[] my : aggregateMonths) {
            int month = my[0];
            int year = my[1];
            List<MonthlyRevenueSummary> actuals =
                    Objects.requireNonNullElse(getAllClientsMonthlyRevenue(month, year), List.of());
            for (MonthlyRevenueSummary actual : actuals) {
                Optional<CustomerService.BuCustomerRef> customer =
                        customerService.resolveBuCustomer(actual.customerId());
                UUID customerUuid = customer.map(CustomerService.BuCustomerRef::id).orElse(null);
                BigDecimal planned = BigDecimal.ZERO;
                if (customerUuid != null && plannedRevenueLookup != null) {
                    planned = Objects.requireNonNullElse(
                            plannedRevenueLookup.plannedTotal(customerUuid, month, year),
                            BigDecimal.ZERO);
                }
                VsPlanAccum acc = vsPlanMap.computeIfAbsent(actual.customerId(), k -> {
                    VsPlanAccum a = new VsPlanAccum();
                    a.customerName = customer
                            .map(CustomerService.BuCustomerRef::customerName)
                            .orElse(actual.customerId());
                    return a;
                });
                acc.planned = acc.planned.add(planned);
                acc.actual = acc.actual.add(actual.netRevenue());
                acc.actualInr = acc.actualInr.add(actual.netRevenueInr());
            }

            findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, month, year)
                    .ifPresent(upload -> {
                        for (RevenueInvoice inv : revenueInvoiceRepository.findByRevenueUploadId(upload.getId())) {
                            if (inv.getAmountUsd() != null) {
                                VsPlanAccum vs = vsPlanMap.computeIfAbsent(inv.getCustomerId(), k -> {
                                    VsPlanAccum a = new VsPlanAccum();
                                    a.customerName = customerService.resolveBuCustomer(inv.getCustomerId())
                                            .map(CustomerService.BuCustomerRef::customerName)
                                            .orElse(inv.getCustomerId());
                                    return a;
                                });
                                vs.actualUsd = vs.actualUsd.add(inv.getAmountUsd());
                                vs.hasUsd = true;
                            }
                            String st = inv.getStatus() != null ? inv.getStatus() : "Unknown";
                            InvoiceStatusBucketAccum acc = statusMap.computeIfAbsent(
                                    st, k -> new InvoiceStatusBucketAccum());
                            acc.count++;
                            acc.total = acc.total.add(inv.getAmount());
                            acc.totalInr = acc.totalInr.add(nullToZero(inv.getAmountInr()));

                            if (isPaidStatus(inv.getStatus()) || isVoidStatus(inv.getStatus())) {
                                continue;
                            }
                            if (inv.getInvoiceDate() == null) {
                                continue;
                            }
                            DsoAccum dso = dsoMap.computeIfAbsent(inv.getCustomerId(), k -> new DsoAccum());
                            long days = ChronoUnit.DAYS.between(inv.getInvoiceDate(), today);
                            dso.totalDays += Math.max(days, 0);
                            dso.count++;
                            dso.outstanding = dso.outstanding.add(nullToZero(inv.getBalance()));
                            if (dso.oldestInvoiceDate == null
                                    || inv.getInvoiceDate().isBefore(dso.oldestInvoiceDate)) {
                                dso.oldestInvoiceDate = inv.getInvoiceDate();
                            }
                        }
                    });

            findActiveUpload(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, month, year)
                    .ifPresent(upload -> {
                        for (RevenueCreditNote note : revenueCreditNoteRepository.findByRevenueUploadId(upload.getId())) {
                            if (note.getAmountUsd() != null) {
                                VsPlanAccum vs = vsPlanMap.computeIfAbsent(note.getCustomerId(), k -> {
                                    VsPlanAccum a = new VsPlanAccum();
                                    a.customerName = customerService.resolveBuCustomer(note.getCustomerId())
                                            .map(CustomerService.BuCustomerRef::customerName)
                                            .orElse(note.getCustomerId());
                                    return a;
                                });
                                vs.actualUsd = vs.actualUsd.subtract(note.getAmountUsd());
                                vs.hasUsd = true;
                            }
                        }
                    });
        }

        List<RevenueVsPlanRow> vsPlan = vsPlanMap.entrySet().stream()
                .map(e -> new RevenueVsPlanRow(
                        e.getKey(),
                        e.getValue().customerName,
                        e.getValue().planned,
                        e.getValue().actual,
                        e.getValue().actualInr,
                        e.getValue().actual.subtract(e.getValue().planned),
                        e.getValue().actualInr.subtract(e.getValue().planned),
                        e.getValue().hasUsd ? e.getValue().actualUsd : null))
                .sorted(Comparator.comparing(RevenueVsPlanRow::customerId))
                .toList();

        List<InvoiceStatusBucket> statusSummary = statusMap.entrySet().stream()
                .map(e -> new InvoiceStatusBucket(
                        e.getKey(), e.getValue().count, e.getValue().total, e.getValue().totalInr))
                .sorted(Comparator.comparing(InvoiceStatusBucket::status))
                .toList();

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

        List<MonthCovered> monthsCovered = monthsWithData.stream()
                .map(my -> new MonthCovered(my[0], my[1], monthLabel(my[0], my[1])))
                .toList();

        String periodLabel = periodLabel(granularity, periodMonth, periodYear, resolvedQuarter);
        String coverageNote = coverageNote(granularity, monthsCovered, scopeMonths.size());

        int responseMonth = "QUARTERLY".equals(granularity)
                ? firstMonthOfQuarter(resolvedQuarter)
                : ("ANNUAL".equals(granularity) ? 4 : periodMonth);
        int responseYear = "ANNUAL".equals(granularity)
                ? fiscalStartYear(periodMonth, periodYear)
                : ("QUARTERLY".equals(granularity)
                        ? yearForQuarterMonth(resolvedQuarter, periodMonth, periodYear)
                        : periodYear);

        return new DashboardResponse(
                responseMonth,
                responseYear,
                granularity,
                "QUARTERLY".equals(granularity) ? resolvedQuarter : null,
                periodLabel,
                monthsCovered,
                coverageNote,
                vsPlan,
                statusSummary,
                dsoRows);
    }

    /** Active invoice/credit-note periods for the dashboard month selector. */
    public List<PeriodWithData> listPeriodsWithData() {
        return revenueUploadRepository.findAll().stream()
                .filter(u -> u.getStatus() == RevenueUploadStatus.ACTIVE)
                .filter(u -> u.getImportType() == RevenueImportType.ZOHO_BOOKS_INVOICES
                        || u.getImportType() == RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES)
                .map(u -> new int[]{u.getPeriodMonth(), u.getPeriodYear()})
                .collect(Collectors.toMap(
                        my -> my[1] * 100 + my[0],
                        my -> my,
                        (a, b) -> a))
                .values().stream()
                .sorted(Comparator
                        .comparingInt((int[] my) -> fiscalSortKey(my[0], my[1]))
                        .reversed())
                .map(my -> new PeriodWithData(my[0], my[1], monthLabel(my[0], my[1])))
                .toList();
    }

    private boolean hasActiveUpload(int periodMonth, int periodYear) {
        return findActiveUpload(RevenueImportType.ZOHO_BOOKS_INVOICES, periodMonth, periodYear).isPresent()
                || findActiveUpload(RevenueImportType.ZOHO_BOOKS_CREDIT_NOTES, periodMonth, periodYear).isPresent();
    }

    private static String normalizeGranularity(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MONTHLY";
        }
        String g = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MONTHLY", "QUARTERLY", "ANNUAL").contains(g)) {
            throw new RevenueBadRequestException(
                    "granularity must be MONTHLY, QUARTERLY, or ANNUAL");
        }
        return g;
    }

    private static int resolveQuarter(String granularity, Integer quarter, int periodMonth) {
        if (!"QUARTERLY".equals(granularity)) {
            return quarterForMonth(periodMonth);
        }
        if (quarter == null) {
            return quarterForMonth(periodMonth);
        }
        if (quarter < 1 || quarter > 4) {
            throw new RevenueBadRequestException("quarter must be between 1 and 4");
        }
        return quarter;
    }

    private static List<int[]> monthsInScope(
            String granularity, int periodMonth, int periodYear, int quarter) {
        if ("MONTHLY".equals(granularity)) {
            return List.of(new int[]{periodMonth, periodYear});
        }
        int fyStart = fiscalStartYear(periodMonth, periodYear);
        if ("QUARTERLY".equals(granularity)) {
            int[] months = monthsOfQuarter(quarter);
            List<int[]> result = new ArrayList<>(3);
            for (int m : months) {
                result.add(new int[]{m, m >= 4 ? fyStart : fyStart + 1});
            }
            return result;
        }
        // ANNUAL — full Indian FY containing periodMonth/periodYear
        int[] months = {4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3};
        List<int[]> result = new ArrayList<>(12);
        for (int m : months) {
            result.add(new int[]{m, m >= 4 ? fyStart : fyStart + 1});
        }
        return result;
    }

    private static int[] monthsOfQuarter(int quarter) {
        return switch (quarter) {
            case 2 -> new int[]{7, 8, 9};
            case 3 -> new int[]{10, 11, 12};
            case 4 -> new int[]{1, 2, 3};
            default -> new int[]{4, 5, 6};
        };
    }

    private static int firstMonthOfQuarter(int quarter) {
        return monthsOfQuarter(quarter)[0];
    }

    private static int quarterForMonth(int month) {
        if (month >= 4 && month <= 6) return 1;
        if (month >= 7 && month <= 9) return 2;
        if (month >= 10 && month <= 12) return 3;
        return 4;
    }

    private static int fiscalStartYear(int periodMonth, int periodYear) {
        return periodMonth >= 4 ? periodYear : periodYear - 1;
    }

    private static int yearForQuarterMonth(int quarter, int periodMonth, int periodYear) {
        int fyStart = fiscalStartYear(periodMonth, periodYear);
        return quarter <= 3 ? fyStart : fyStart + 1;
    }

    private static String fiscalYearLabel(int periodMonth, int periodYear) {
        int start = fiscalStartYear(periodMonth, periodYear);
        int startYy = start % 100;
        int endYy = (start + 1) % 100;
        return String.format("FY%02d%02d", startYy, endYy);
    }

    private static String periodLabel(
            String granularity, int periodMonth, int periodYear, int quarter) {
        return switch (granularity) {
            case "QUARTERLY" -> "Q" + quarter + " " + fiscalYearLabel(periodMonth, periodYear);
            case "ANNUAL" -> fiscalYearLabel(periodMonth, periodYear);
            default -> monthLabel(periodMonth, periodYear);
        };
    }

    private static String monthLabel(int month, int year) {
        String[] names = {
                "", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        };
        return names[month] + " " + year;
    }

    private static String shortMonth(int month) {
        String[] names = {
                "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        };
        return names[month];
    }

    private static String coverageNote(
            String granularity, List<MonthCovered> monthsCovered, int scopeSize) {
        if (!"ANNUAL".equals(granularity) && !"QUARTERLY".equals(granularity)) {
            return null;
        }
        if (monthsCovered.isEmpty()) {
            return "Data available: none (0 of " + scopeSize + " months)";
        }
        MonthCovered first = monthsCovered.getFirst();
        MonthCovered last = monthsCovered.getLast();
        String range = first.month() == last.month() && first.year() == last.year()
                ? shortMonth(first.month()) + " " + first.year()
                : shortMonth(first.month()) + "–" + shortMonth(last.month()) + " " + last.year();
        // If years differ (e.g. Apr–Mar), show both years on ends
        if (first.year() != last.year()) {
            range = shortMonth(first.month()) + " " + first.year()
                    + "–" + shortMonth(last.month()) + " " + last.year();
        }
        return "Data available: " + range + " (" + monthsCovered.size() + " of " + scopeSize + " months)";
    }

    /** Sort key for Indian FY order (Apr…Mar) ascending. */
    private static int fiscalSortKey(int month, int year) {
        int fyStart = fiscalStartYear(month, year);
        int order = month >= 4 ? month - 4 : month + 8;
        return fyStart * 12 + order;
    }

    private static class VsPlanAccum {
        String customerName;
        BigDecimal planned = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal actualInr = BigDecimal.ZERO;
        BigDecimal actualUsd = BigDecimal.ZERO;
        boolean hasUsd;
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

    /**
     * Parses invoice/credit-note currency. Null/blank (unmapped Currency column) defaults to
     * {@link RevenueCurrency#INR} so no FX conversion is applied. Only explicit USD uses FX
     * (ADR-017). Note: Revenue spec §4 mentions customer billing-currency default — product
     * decision for unmapped Currency is INR (no FX).
     */
    private static RevenueCurrency parseCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return RevenueCurrency.INR;
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

    /** Callers supply planned totals (e.g. from Budgeting) without creating a module cycle. */
    @FunctionalInterface
    public interface PlannedRevenueLookup {
        BigDecimal plannedTotal(UUID customerId, int month, int year);
    }

    // ── Backup / restore (ADR-044 Tier 2) ────────────────────────────────────

    public List<BackupSheet> exportBackupSheets() {
        return revenueModuleBackup.exportBackupSheets();
    }

    @Transactional
    public void wipeForRestore() {
        revenueModuleBackup.wipeRevenueData();
    }

    @Transactional
    public Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        return revenueModuleBackup.restoreBackupSheets(rowsByFile);
    }
}
