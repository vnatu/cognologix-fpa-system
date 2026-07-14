package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.*;
import com.cognologix.fpa.customer.dto.CustomerDetailResponse;
import com.cognologix.fpa.customer.dto.CustomerImportConflictsResponse;
import com.cognologix.fpa.customer.dto.CustomerImportResponse;
import com.cognologix.fpa.customer.dto.CustomerImportRowError;
import com.cognologix.fpa.customer.dto.RateCardImportResponse;
import com.cognologix.fpa.customer.dto.RateCardImportRowError;
import com.cognologix.fpa.customer.dto.ProjectCodeImportResponse;
import com.cognologix.fpa.customer.dto.ProjectCodeImportRowError;
import com.cognologix.fpa.customer.dto.RateCardImportSkipped;
import com.cognologix.fpa.customer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public API surface for the Customer Management module.
 * Controllers and other modules call this class only — never sub-package types directly (ADR-008).
 * No dependency on the general module — FX rate lookups happen in GeneralConfigService (ADR-018).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerProjectCodeRepository projectCodeRepository;
    private final RateCardRepository rateCardRepository;
    private final CommercialTermsRepository commercialTermsRepository;
    private final ConcentrationWatchGroupRepository concentrationWatchGroupRepository;
    private final CustomerImportParser customerImportParser;
    private final RateCardImportParser rateCardImportParser;
    private final ProjectCodeImportParser projectCodeImportParser;
    private final CustomerExcelExporter customerExcelExporter;

    // ── Customer Master ──────────────────────────────────────────────────────

    public List<Customer> findAllCustomers(boolean includeInternal) {
        return includeInternal
                ? customerRepository.findAll()
                : customerRepository.findByInternalFalse();
    }

    public List<Customer> findAllCustomers() {
        return findAllCustomers(false);
    }

    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
    }

    public Optional<CustomerDetailResponse> getCustomerDetail(UUID customerId) {
        return customerRepository.findById(customerId).map(this::toCustomerDetailResponse);
    }

    public Optional<Customer> findByCustomerCode(String customerCode) {
        return customerRepository.findByCustomerCode(customerCode);
    }

    public List<Customer> findByLifecycleStatus(LifecycleStatus status) {
        return customerRepository.findByLifecycleStatus(status);
    }

    @Transactional
    public Customer createCustomer(String customerCode,
                                   String customerName,
                                   String zohoBooksCustomerRef,
                                   String relationshipOwnerEmployeeId,
                                   LifecycleStatus lifecycleStatus,
                                   Integer dsoDays) {
        if (customerRepository.existsByCustomerCode(customerCode)) {
            throw new IllegalArgumentException("Customer code already exists: " + customerCode);
        }
        var customer = Customer.builder()
                .customerCode(customerCode)
                .customerName(customerName)
                .zohoBooksCustomerRef(zohoBooksCustomerRef)
                .relationshipOwnerEmployeeId(relationshipOwnerEmployeeId)
                .lifecycleStatus(lifecycleStatus)
                .build();
        customer = customerRepository.save(customer);
        if (dsoDays != null) {
            var terms = CommercialTerms.builder().customer(customer).dsoDays(dsoDays).build();
            commercialTermsRepository.save(terms);
        }
        return customer;
    }

    /**
     * Consolidated update for mutable customer fields. Null values are ignored (no-op for that field).
     * DSO is upserted on CommercialTerms when provided.
     * No DELETE on customers — use lifecycleStatus=CHURNED to retire a customer (spec §7.2).
     */
    @Transactional
    public CustomerDetailResponse updateCustomer(UUID customerId,
                                                 String customerCode,
                                                 String customerName,
                                                 LifecycleStatus lifecycleStatus,
                                                 String relationshipOwnerEmployeeId,
                                                 Integer dsoDays) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        if (customerCode != null && !customerCode.isBlank()) {
            String trimmedCode = customerCode.trim();
            if (!trimmedCode.equals(customer.getCustomerCode())) {
                if (!customer.isInternal()) {
                    throw new IllegalArgumentException(
                            "Customer code cannot be changed for external clients");
                }
                if (customerRepository.existsByCustomerCode(trimmedCode)) {
                    throw new IllegalArgumentException("Customer code already exists: " + trimmedCode);
                }
                customer.setCustomerCode(trimmedCode);
            }
        }
        if (customerName != null) customer.setCustomerName(customerName);
        if (lifecycleStatus != null) customer.setLifecycleStatus(lifecycleStatus);
        if (!customer.isInternal() && relationshipOwnerEmployeeId != null) {
            customer.setRelationshipOwnerEmployeeId(relationshipOwnerEmployeeId);
        }
        if (!customer.isInternal() && dsoDays != null) {
            var terms = commercialTermsRepository.findById(customerId)
                    .orElse(CommercialTerms.builder().customer(customer).build());
            terms.setDsoDays(dsoDays);
            commercialTermsRepository.save(terms);
        }
        return toCustomerDetailResponse(customerRepository.save(customer));
    }

    private CustomerDetailResponse toCustomerDetailResponse(Customer customer) {
        var terms = commercialTermsRepository.findById(customer.getId()).orElse(null);
        var projectCodes = projectCodeRepository.findByCustomerId(customer.getId());
        return CustomerDetailResponse.from(customer, terms, projectCodes);
    }

    // ── Project Codes ────────────────────────────────────────────────────────

    public List<CustomerProjectCode> findProjectCodesByCustomer(UUID customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        return projectCodeRepository.findByCustomerId(customerId);
    }

    @Transactional
    public CustomerProjectCode addProjectCode(UUID customerId, String projectCode, String description) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        var pc = CustomerProjectCode.builder()
                .customer(customer)
                .projectCode(projectCode)
                .description(description)
                .build();
        return projectCodeRepository.save(pc);
    }

    @Transactional
    public void removeProjectCode(UUID customerId, UUID codeId) {
        var pc = projectCodeRepository.findByIdAndCustomerId(codeId, customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project code not found or does not belong to this customer"));
        projectCodeRepository.delete(pc);
    }

    // ── Rate Cards ───────────────────────────────────────────────────────────

    public List<RateCard> findRateCardsByCustomer(UUID customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        return rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId);
    }

    public Optional<RateCard> findActiveRateCard(UUID customerId) {
        return rateCardRepository.findByCustomerIdAndEffectiveToIsNull(customerId);
    }

    public Optional<RateCard> findRateCardOnDate(UUID customerId, LocalDate asOf) {
        return rateCardRepository.findActiveOnDate(customerId, asOf);
    }

    /**
     * Creates a new rate card. Automatically closes any existing open rate card by setting
     * its effective_to = newCard.effectiveFrom - 1 day (effective-dated model, spec §6).
     * No PUT on rate cards — supersede by creating a new card.
     */
    @Transactional
    public RateCard createRateCard(UUID customerId,
                                   String name,
                                   RateCardType type,
                                   RateCurrency currency,
                                   LocalDate effectiveFrom,
                                   List<RateCardLine> lines) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        rateCardRepository.findByCustomerIdAndEffectiveToIsNull(customerId).ifPresent(existing -> {
            existing.setEffectiveTo(effectiveFrom.minusDays(1));
            rateCardRepository.save(existing);
        });
        var card = RateCard.builder()
                .customer(customer)
                .name(name)
                .rateCardType(type)
                .currency(currency)
                .effectiveFrom(effectiveFrom)
                .lines(lines)
                .build();
        lines.forEach(l -> l.setRateCard(card));
        return rateCardRepository.save(card);
    }

    /**
     * Creates a rate card only when the customer has no active card (effective_to IS NULL).
     * Used by bulk import — import skips groups when an active card already exists (spec §6).
     */
    @Transactional
    public Optional<RateCard> createRateCardIfNoActive(UUID customerId,
                                                       String name,
                                                       RateCardType type,
                                                       RateCurrency currency,
                                                       LocalDate effectiveFrom,
                                                       List<RateCardLine> lines) {
        if (rateCardRepository.findByCustomerIdAndEffectiveToIsNull(customerId).isPresent()) {
            return Optional.empty();
        }
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        var card = RateCard.builder()
                .customer(customer)
                .name(name)
                .rateCardType(type)
                .currency(currency)
                .effectiveFrom(effectiveFrom)
                .lines(lines)
                .build();
        lines.forEach(l -> l.setRateCard(card));
        return Optional.of(rateCardRepository.save(card));
    }

    public byte[] buildRateCardImportSample() {
        return rateCardImportParser.buildSampleWorkbook();
    }

    public byte[] exportCustomers() {
        return customerExcelExporter.exportCustomers();
    }

    public byte[] exportRateCards() {
        return customerExcelExporter.exportRateCards();
    }

    public byte[] exportProjectCodes() {
        return customerExcelExporter.exportProjectCodes();
    }

    public byte[] buildProjectCodeImportSample() {
        return projectCodeImportParser.buildSampleWorkbook();
    }

    @Transactional
    public ProjectCodeImportResponse importProjectCodes(MultipartFile file) {
        List<ProjectCodeImportParser.ParsedProjectCodeImportRow> rows = projectCodeImportParser.parse(file);
        int created = 0;
        int skipped = 0;
        List<ProjectCodeImportRowError> errors = new ArrayList<>();

        for (var row : rows) {
            String customerCode = blankToNull(row.customerCode());
            String projectCode = blankToNull(row.projectCode());
            String description = blankToNull(row.description());

            if (customerCode == null) {
                errors.add(new ProjectCodeImportRowError(
                        row.rowNumber(), null, projectCode, "Customer Code is required"));
                continue;
            }
            if (projectCode == null) {
                errors.add(new ProjectCodeImportRowError(
                        row.rowNumber(), customerCode, null, "Project Code is required"));
                continue;
            }

            var customerOpt = customerRepository.findByCustomerCode(customerCode);
            if (customerOpt.isEmpty()) {
                errors.add(new ProjectCodeImportRowError(
                        row.rowNumber(), customerCode, projectCode,
                        "Customer Code not found: " + customerCode));
                continue;
            }

            var customer = customerOpt.get();
            if (projectCodeRepository.findByCustomerIdAndProjectCode(customer.getId(), projectCode).isPresent()) {
                skipped++;
                continue;
            }

            projectCodeRepository.save(CustomerProjectCode.builder()
                    .customer(customer)
                    .projectCode(projectCode)
                    .description(description)
                    .build());
            created++;
        }

        return new ProjectCodeImportResponse(rows.size(), created, skipped, errors);
    }

    @Transactional
    public RateCardImportResponse importRateCards(MultipartFile file) {
        List<RateCardImportParser.ParsedRateCardImportRow> parsedRows = rateCardImportParser.parse(file);
        List<RateCardImportRowError> errors = new ArrayList<>();
        List<ValidatedRateCardRow> validRows = new ArrayList<>();

        for (var row : parsedRows) {
            Optional<ValidatedRateCardRow> validated = validateRateCardRow(row, errors);
            validated.ifPresent(validRows::add);
        }

        Map<RateCardGroupKey, List<ValidatedRateCardRow>> groups = validRows.stream()
                .collect(Collectors.groupingBy(
                        r -> new RateCardGroupKey(r.customerCode(), r.rateCardName(), r.effectiveFrom()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Set<ValidatedRateCardRow> rowsToImport = new LinkedHashSet<>();
        for (var entry : groups.entrySet()) {
            validateRateCardGroup(entry.getKey(), entry.getValue(), errors, rowsToImport);
        }

        int created = 0;
        int skipped = 0;
        List<RateCardImportSkipped> skippedGroups = new ArrayList<>();

        Map<RateCardGroupKey, List<ValidatedRateCardRow>> importGroups = rowsToImport.stream()
                .collect(Collectors.groupingBy(
                        r -> new RateCardGroupKey(r.customerCode(), r.rateCardName(), r.effectiveFrom()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map.Entry<RateCardGroupKey, List<ValidatedRateCardRow>>> sortedImportGroups = importGroups.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<RateCardGroupKey, List<ValidatedRateCardRow>> e) ->
                                e.getKey().customerCode())
                        .thenComparing(e -> e.getKey().effectiveFrom()))
                .toList();

        for (var entry : sortedImportGroups) {
            RateCardGroupKey key = entry.getKey();
            List<ValidatedRateCardRow> groupRows = entry.getValue();
            var customer = customerRepository.findByCustomerCode(key.customerCode()).orElse(null);
            if (customer == null) {
                continue;
            }

            ValidatedRateCardRow header = groupRows.getFirst();
            List<RateCardLine> lines = groupRows.stream()
                    .map(r -> RateCardLine.builder()
                            .jobLevel(r.jobLevel())
                            .rateAmount(r.rateAmount())
                            .build())
                    .toList();

            Optional<RateCard> createdCard = createRateCardIfNoActive(
                    customer.getId(),
                    header.rateCardName(),
                    header.rateCardType(),
                    header.currency(),
                    header.effectiveFrom(),
                    lines);

            if (createdCard.isPresent()) {
                created++;
            } else {
                skipped++;
                skippedGroups.add(new RateCardImportSkipped(
                        key.customerCode(),
                        key.rateCardName(),
                        key.effectiveFrom().toString()));
            }
        }

        return new RateCardImportResponse(
                parsedRows.size(), created, skipped, errors, skippedGroups);
    }

    private Optional<ValidatedRateCardRow> validateRateCardRow(
            RateCardImportParser.ParsedRateCardImportRow row,
            List<RateCardImportRowError> errors) {
        String customerCode = blankToNull(row.customerCode());
        String rateCardName = blankToNull(row.rateCardName());
        String typeRaw = blankToNull(row.rateCardType());
        String currencyRaw = blankToNull(row.currency());
        String effectiveFromRaw = blankToNull(row.effectiveFrom());
        String jobLevel = blankToNull(row.jobLevel());
        String rateAmountRaw = blankToNull(row.rateAmount());

        if (customerCode == null) {
            errors.add(error(row, "Customer Code is required"));
            return Optional.empty();
        }
        if (rateCardName == null) {
            errors.add(error(row, customerCode, "Rate Card Name is required"));
            return Optional.empty();
        }
        if (typeRaw == null) {
            errors.add(error(row, customerCode, rateCardName, "Rate Card Type is required"));
            return Optional.empty();
        }
        RateCardType rateCardType;
        try {
            rateCardType = RateCardType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(error(row, customerCode, rateCardName,
                    "Rate Card Type must be FLAT or TIERED"));
            return Optional.empty();
        }
        if (currencyRaw == null) {
            errors.add(error(row, customerCode, rateCardName, "Currency is required"));
            return Optional.empty();
        }
        RateCurrency currency;
        try {
            currency = RateCurrency.valueOf(currencyRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(error(row, customerCode, rateCardName, "Currency must be USD or INR"));
            return Optional.empty();
        }
        if (effectiveFromRaw == null) {
            errors.add(error(row, customerCode, rateCardName, "Effective From is required"));
            return Optional.empty();
        }
        LocalDate effectiveFrom = parseEffectiveFrom(effectiveFromRaw);
        if (effectiveFrom == null) {
            errors.add(error(row, customerCode, rateCardName, "Effective From is not a valid date"));
            return Optional.empty();
        }
        if (rateAmountRaw == null) {
            errors.add(error(row, customerCode, rateCardName, "Rate Amount is required"));
            return Optional.empty();
        }
        BigDecimal rateAmount = parsePositiveAmount(rateAmountRaw);
        if (rateAmount == null) {
            errors.add(error(row, customerCode, rateCardName, "Rate Amount must be a positive number"));
            return Optional.empty();
        }
        if (rateCardType == RateCardType.TIERED && jobLevel == null) {
            errors.add(error(row, customerCode, rateCardName,
                    "Job Level is required for TIERED rate cards"));
            return Optional.empty();
        }
        if (rateCardType == RateCardType.FLAT) {
            jobLevel = null;
        }
        if (!customerRepository.existsByCustomerCode(customerCode)) {
            errors.add(error(row, customerCode, rateCardName,
                    "Customer Code not found: " + customerCode));
            return Optional.empty();
        }

        return Optional.of(new ValidatedRateCardRow(
                row.rowNumber(),
                customerCode,
                rateCardName,
                rateCardType,
                currency,
                effectiveFrom,
                jobLevel,
                rateAmount));
    }

    private void validateRateCardGroup(
            RateCardGroupKey key,
            List<ValidatedRateCardRow> rows,
            List<RateCardImportRowError> errors,
            Set<ValidatedRateCardRow> rowsToImport) {
        ValidatedRateCardRow first = rows.getFirst();
        RateCardType expectedType = first.rateCardType();
        RateCurrency expectedCurrency = first.currency();

        boolean inconsistent = rows.stream().anyMatch(r ->
                r.rateCardType() != expectedType || !r.currency().equals(expectedCurrency));
        if (inconsistent) {
            for (ValidatedRateCardRow row : rows) {
                errors.add(new RateCardImportRowError(
                        row.rowNumber(),
                        row.customerCode(),
                        row.rateCardName(),
                        "Rate Card Type and Currency must be consistent within a rate card group"));
            }
            return;
        }

        if (expectedType == RateCardType.FLAT && rows.size() > 1) {
            for (ValidatedRateCardRow row : rows) {
                errors.add(new RateCardImportRowError(
                        row.rowNumber(),
                        row.customerCode(),
                        row.rateCardName(),
                        "FLAT rate card allows only one line per group"));
            }
            return;
        }

        rowsToImport.addAll(rows);
    }

    private static RateCardImportRowError error(
            RateCardImportParser.ParsedRateCardImportRow row, String reason) {
        return new RateCardImportRowError(row.rowNumber(), null, null, reason);
    }

    private static RateCardImportRowError error(
            RateCardImportParser.ParsedRateCardImportRow row,
            String customerCode,
            String reason) {
        return new RateCardImportRowError(row.rowNumber(), customerCode, null, reason);
    }

    private static RateCardImportRowError error(
            RateCardImportParser.ParsedRateCardImportRow row,
            String customerCode,
            String rateCardName,
            String reason) {
        return new RateCardImportRowError(row.rowNumber(), customerCode, rateCardName, reason);
    }

    private static LocalDate parseEffectiveFrom(String raw) {
        String value = raw.trim();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM-uuuu"),
                DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static BigDecimal parsePositiveAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw.trim().replace(",", ""));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RateCardGroupKey(String customerCode, String rateCardName, LocalDate effectiveFrom) {}

    private record ValidatedRateCardRow(
            int rowNumber,
            String customerCode,
            String rateCardName,
            RateCardType rateCardType,
            RateCurrency currency,
            LocalDate effectiveFrom,
            String jobLevel,
            BigDecimal rateAmount
    ) {}

    // ── Commercial Terms ─────────────────────────────────────────────────────

    @Transactional
    public CommercialTerms upsertCommercialTerms(UUID customerId, int dsoDays) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        var terms = commercialTermsRepository.findById(customerId)
                .orElse(CommercialTerms.builder().customer(customer).build());
        terms.setDsoDays(dsoDays);
        return commercialTermsRepository.save(terms);
    }

    // ── Concentration Watch Groups ────────────────────────────────────────────

    public List<ConcentrationWatchGroup> findAllWatchGroups() {
        return concentrationWatchGroupRepository.findAll();
    }

    // ── Cross-module API (People & Payroll BU validation, Module 2 §5) ──────

    public boolean isKnownCustomer(String customerCodeOrName) {
        if (customerCodeOrName == null || customerCodeOrName.isBlank()) {
            return false;
        }
        String key = customerCodeOrName.trim();
        return customerRepository.existsByCustomerCode(key)
                || customerRepository.existsByCustomerCodeOrCustomerName(key, key)
                || customerRepository.existsByCustomerCodeAndInternalTrue(key);
    }

    /**
     * Whether the BU value maps to an internal organisational unit (ADR-029), not an external client.
     */
    public boolean isInternalBu(String customerCodeOrName) {
        if (customerCodeOrName == null || customerCodeOrName.isBlank()) {
            return false;
        }
        String key = customerCodeOrName.trim();
        return customerRepository.findByCustomerCodeOrCustomerName(key, key)
                .map(Customer::isInternal)
                .orElse(false);
    }

    /**
     * Resolves a Zoho People BU (code or name) to Customer Master code, name, and internal flag (ADR-029).
     */
    public Optional<BuCustomerRef> resolveBuCustomer(String customerCodeOrName) {
        if (customerCodeOrName == null || customerCodeOrName.isBlank()) {
            return Optional.empty();
        }
        String key = customerCodeOrName.trim();
        return customerRepository.findByCustomerCodeOrCustomerName(key, key)
                .map(c -> new BuCustomerRef(c.getCustomerCode(), c.getCustomerName(), c.isInternal()));
    }

    /** Lightweight cross-module BU identity — avoids exposing Customer entity (ADR-008). */
    public record BuCustomerRef(String customerCode, String customerName, boolean internal) {}

    /**
     * Resolves a Zoho People project code to the parent customer's customer_code (Module 2 §9).
     * Returns null when no mapping exists.
     */
    public String findCustomerCodeByProjectCode(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            return null;
        }
        return projectCodeRepository.findByProjectCode(projectCode.trim())
                .map(pc -> pc.getCustomer().getCustomerCode())
                .orElse(null);
    }

    // ── Customer Import (ADR-027) ─────────────────────────────────────────────

    public CustomerImportConflictsResponse detectImportConflicts(MultipartFile file) {
        List<CustomerImportParser.ParsedCustomerImportRow> rows = customerImportParser.parse(file);
        Set<String> codes = rows.stream()
                .map(CustomerImportParser.ParsedCustomerImportRow::customerCode)
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existing = customerRepository.findByCustomerCodeIn(codes).stream()
                .map(Customer::getCustomerCode)
                .collect(Collectors.toSet());

        List<String> existingCodes = codes.stream().filter(existing::contains).toList();
        List<String> newCodes = codes.stream().filter(code -> !existing.contains(code)).toList();
        return new CustomerImportConflictsResponse(existingCodes, newCodes);
    }

    @Transactional
    public CustomerImportResponse importCustomers(MultipartFile file, ConflictResolution conflictResolution) {
        List<CustomerImportParser.ParsedCustomerImportRow> rows = customerImportParser.parse(file);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<CustomerImportRowError> errors = new ArrayList<>();

        for (var row : rows) {
            String customerCode = row.customerCode() != null ? row.customerCode().trim() : null;
            String customerName = row.customerName() != null ? row.customerName().trim() : null;

            if (customerCode == null || customerCode.isBlank()) {
                errors.add(new CustomerImportRowError(
                        row.rowNumber(), customerCode, "Customer Code is required"));
                continue;
            }
            if (customerName == null || customerName.isBlank()) {
                errors.add(new CustomerImportRowError(
                        row.rowNumber(), customerCode, "Customer Name is required"));
                continue;
            }

            LifecycleStatus lifecycleStatus =
                    CustomerImportParser.parseLifecycleStatus(row.lifecycleStatusRaw());
            int dsoDays = CustomerImportParser.parseDsoDays(row.dsoDaysRaw());
            String zohoRef = blankToNull(row.zohoBooksCustomerRef());
            String ownerId = blankToNull(row.relationshipOwnerEmployeeId());

            Optional<Customer> existing = customerRepository.findByCustomerCode(customerCode);
            if (existing.isPresent()) {
                if (conflictResolution == ConflictResolution.SKIP) {
                    skipped++;
                    continue;
                }
                try {
                    replaceCustomerFromImport(
                            existing.get(), customerName, zohoRef, lifecycleStatus, ownerId, dsoDays);
                    updated++;
                } catch (DataIntegrityViolationException e) {
                    errors.add(new CustomerImportRowError(
                            row.rowNumber(), customerCode, "Update failed: " + rootCauseMessage(e)));
                }
                continue;
            }

            try {
                createCustomer(customerCode, customerName, zohoRef, ownerId, lifecycleStatus, dsoDays);
                created++;
            } catch (IllegalArgumentException | DataIntegrityViolationException e) {
                errors.add(new CustomerImportRowError(
                        row.rowNumber(), customerCode, rootCauseMessage(e)));
            }
        }

        return new CustomerImportResponse(rows.size(), created, updated, skipped, errors);
    }

    @Transactional
    public Customer replaceCustomerFromImport(Customer customer,
                                            String customerName,
                                            String zohoBooksCustomerRef,
                                            LifecycleStatus lifecycleStatus,
                                            String relationshipOwnerEmployeeId,
                                            int dsoDays) {
        customer.setCustomerName(customerName);
        customer.setZohoBooksCustomerRef(zohoBooksCustomerRef);
        customer.setLifecycleStatus(lifecycleStatus);
        customer.setRelationshipOwnerEmployeeId(relationshipOwnerEmployeeId);
        var terms = commercialTermsRepository.findById(customer.getId())
                .orElse(CommercialTerms.builder().customer(customer).build());
        terms.setDsoDays(dsoDays);
        commercialTermsRepository.save(terms);
        return customerRepository.save(customer);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String rootCauseMessage(Exception e) {
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
    }
}
