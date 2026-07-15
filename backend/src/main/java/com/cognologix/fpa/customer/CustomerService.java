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
    private final RateCardProjectCodeRepository rateCardProjectCodeRepository;
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
        List<RateCard> cards = rateCardRepository.findByCustomerIdOrderByEffectiveFromDesc(customerId);
        enrichProjectCodeDisplay(cards);
        return cards;
    }

    /** Active blended (no project-code associations) rate card — open-ended. */
    public Optional<RateCard> findActiveRateCard(UUID customerId) {
        return findActiveBlendedRateCard(customerId, LocalDate.now());
    }

    public Optional<RateCard> findRateCardOnDate(UUID customerId, LocalDate asOf) {
        return findActiveBlendedRateCard(customerId, asOf);
    }

    /**
     * Project-scoped rate card covering {@code projectCodeId} on {@code asOf} (ADR-035).
     */
    public Optional<RateCard> findActiveRateCardForProjectCode(
            UUID customerId, UUID projectCodeId, LocalDate asOf) {
        return rateCardRepository.findActiveForProjectOnDate(customerId, projectCodeId, asOf)
                .map(this::enrichProjectCodeDisplay);
    }

    /**
     * Blended rate card (no join-table rows) active on {@code asOf}. Revenue fallback (ADR-035).
     */
    public Optional<RateCard> findActiveBlendedRateCard(UUID customerId, LocalDate asOf) {
        return rateCardRepository.findActiveBlendedOnDate(customerId, asOf)
                .map(this::enrichProjectCodeDisplay);
    }

    /**
     * Creates a new rate card. Empty/null {@code projectCodeIds} = blended.
     * Does not auto-close existing cards (ADR-035).
     */
    @Transactional
    public RateCard createRateCard(UUID customerId,
                                   String name,
                                   RateCardType type,
                                   RateCurrency currency,
                                   LocalDate effectiveFrom,
                                   List<RateCardLine> lines,
                                   List<UUID> projectCodeIds) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        List<UUID> codes = normalizeProjectCodeIds(projectCodeIds);
        validateProjectCodesBelongToCustomer(customerId, codes);
        if (codes.isEmpty()) {
            assertNoActiveBlendedCard(customerId);
        } else {
            assertProjectCodesNotOnActiveCards(customerId, codes);
        }

        var card = RateCard.builder()
                .customer(customer)
                .name(name)
                .rateCardType(type)
                .currency(currency)
                .effectiveFrom(effectiveFrom)
                .lines(lines)
                .build();
        lines.forEach(l -> l.setRateCard(card));
        RateCard saved = rateCardRepository.save(card);
        attachProjectCodes(saved.getId(), codes);
        return enrichProjectCodeDisplay(saved);
    }

    /**
     * Edit via versioning: close current at {@code effectiveTo}, create new from {@code effectiveFrom}.
     * {@code projectCodeIds} null = inherit current associations; empty = blended.
     */
    @Transactional
    public RateCard updateRateCard(UUID customerId,
                                   UUID rateCardId,
                                   LocalDate effectiveTo,
                                   LocalDate effectiveFrom,
                                   String name,
                                   RateCardType type,
                                   RateCurrency currency,
                                   List<RateCardLine> lines,
                                   List<UUID> projectCodeIds) {
        if (effectiveFrom == null) {
            throw new CustomerBadRequestException("effectiveFrom is required");
        }
        if (effectiveTo == null) {
            throw new CustomerBadRequestException("effectiveTo is required");
        }
        if (!effectiveTo.isBefore(effectiveFrom)) {
            throw new CustomerBadRequestException(
                    "effectiveTo must be before effectiveFrom");
        }
        var current = rateCardRepository.findByIdAndCustomerId(rateCardId, customerId)
                .orElseThrow(() -> new CustomerBadRequestException(
                        "Rate card not found or does not belong to this customer"));

        List<UUID> codes;
        if (projectCodeIds == null) {
            codes = rateCardProjectCodeRepository.findByRateCardId(rateCardId).stream()
                    .map(RateCardProjectCode::getProjectCodeId)
                    .toList();
        } else {
            codes = normalizeProjectCodeIds(projectCodeIds);
        }
        validateProjectCodesBelongToCustomer(customerId, codes);

        current.setEffectiveTo(effectiveTo);
        rateCardRepository.saveAndFlush(current);

        if (codes.isEmpty()) {
            assertNoActiveBlendedCard(customerId);
        } else {
            assertProjectCodesNotOnActiveCards(customerId, codes);
        }

        var newCard = RateCard.builder()
                .customer(current.getCustomer())
                .name(name)
                .rateCardType(type)
                .currency(currency)
                .effectiveFrom(effectiveFrom)
                .lines(lines)
                .build();
        lines.forEach(l -> l.setRateCard(newCard));
        RateCard saved = rateCardRepository.save(newCard);
        attachProjectCodes(saved.getId(), codes);
        return enrichProjectCodeDisplay(saved);
    }

    /**
     * Creates a rate card only when no conflicting active scope exists (ADR-028 / ADR-035).
     * Blended: skips if an active blended card exists. Project-scoped: skips if any
     * requested project code is already on an active card.
     */
    @Transactional
    public Optional<RateCard> createRateCardIfNoActive(UUID customerId,
                                                       String name,
                                                       RateCardType type,
                                                       RateCurrency currency,
                                                       LocalDate effectiveFrom,
                                                       List<RateCardLine> lines,
                                                       List<UUID> projectCodeIds) {
        List<UUID> codes = normalizeProjectCodeIds(projectCodeIds);
        if (codes.isEmpty()) {
            boolean hasBlended = rateCardRepository.findByCustomerIdAndEffectiveToIsNull(customerId).stream()
                    .anyMatch(rc -> rateCardProjectCodeRepository.findByRateCardId(rc.getId()).isEmpty());
            if (hasBlended) {
                return Optional.empty();
            }
        } else if (!rateCardProjectCodeRepository
                .findActiveLinksForCustomerAndProjectCodes(customerId, codes).isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(createRateCard(
                    customerId, name, type, currency, effectiveFrom, lines, codes));
        } catch (CustomerConflictException e) {
            return Optional.empty();
        }
    }

    private static List<UUID> normalizeProjectCodeIds(List<UUID> projectCodeIds) {
        if (projectCodeIds == null || projectCodeIds.isEmpty()) {
            return List.of();
        }
        return projectCodeIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void validateProjectCodesBelongToCustomer(UUID customerId, List<UUID> projectCodeIds) {
        for (UUID projectCodeId : projectCodeIds) {
            projectCodeRepository.findByIdAndCustomerId(projectCodeId, customerId)
                    .orElseThrow(() -> new CustomerBadRequestException(
                            "Project code does not belong to this customer"));
        }
    }

    private void assertNoActiveBlendedCard(UUID customerId) {
        List<RateCard> active = rateCardRepository.findByCustomerIdAndEffectiveToIsNull(customerId);
        for (RateCard rc : active) {
            if (rateCardProjectCodeRepository.findByRateCardId(rc.getId()).isEmpty()) {
                throw new CustomerConflictException(
                        "An active blended rate card already exists: " + rc.getName()
                                + ". Close that rate card first.");
            }
        }
    }

    private void assertProjectCodesNotOnActiveCards(UUID customerId, List<UUID> projectCodeIds) {
        List<RateCardProjectCode> conflicts =
                rateCardProjectCodeRepository.findActiveLinksForCustomerAndProjectCodes(
                        customerId, projectCodeIds);
        if (conflicts.isEmpty()) {
            return;
        }
        RateCardProjectCode conflict = conflicts.getFirst();
        CustomerProjectCode pc = projectCodeRepository.findById(conflict.getProjectCodeId())
                .orElse(null);
        RateCard other = rateCardRepository.findById(conflict.getRateCardId()).orElse(null);
        String codeLabel = pc != null ? pc.getProjectCode() : conflict.getProjectCodeId().toString();
        String cardLabel = other != null ? other.getName() : conflict.getRateCardId().toString();
        throw new CustomerConflictException(
                "Project code [" + codeLabel + "] is already assigned to active rate card ["
                        + cardLabel + "]. Close that rate card first or remove this project code "
                        + "from the request.");
    }

    private void attachProjectCodes(UUID rateCardId, List<UUID> projectCodeIds) {
        for (UUID projectCodeId : projectCodeIds) {
            rateCardProjectCodeRepository.save(RateCardProjectCode.builder()
                    .rateCardId(rateCardId)
                    .projectCodeId(projectCodeId)
                    .build());
        }
    }

    private RateCard enrichProjectCodeDisplay(RateCard card) {
        enrichProjectCodeDisplay(List.of(card));
        return card;
    }

    private void enrichProjectCodeDisplay(List<RateCard> cards) {
        if (cards.isEmpty()) {
            return;
        }
        Set<UUID> cardIds = cards.stream().map(RateCard::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<RateCardProjectCode> links = rateCardProjectCodeRepository.findByRateCardIdIn(cardIds);
        Set<UUID> projectIds = links.stream()
                .map(RateCardProjectCode::getProjectCodeId)
                .collect(Collectors.toSet());
        Map<UUID, CustomerProjectCode> byId = projectIds.isEmpty()
                ? Map.of()
                : projectCodeRepository.findAllById(projectIds).stream()
                        .collect(Collectors.toMap(CustomerProjectCode::getId, pc -> pc));
        Map<UUID, List<RateCardProjectCode>> linksByCard = links.stream()
                .collect(Collectors.groupingBy(RateCardProjectCode::getRateCardId));

        for (RateCard card : cards) {
            List<RateCardProjectCode> cardLinks = linksByCard.getOrDefault(card.getId(), List.of());
            List<RateCard.ProjectCodeSummary> summaries = new ArrayList<>();
            List<String> codeStrings = new ArrayList<>();
            for (RateCardProjectCode link : cardLinks) {
                CustomerProjectCode pc = byId.get(link.getProjectCodeId());
                if (pc == null) {
                    continue;
                }
                summaries.add(new RateCard.ProjectCodeSummary(
                        pc.getId(), pc.getProjectCode(), pc.getDescription()));
                codeStrings.add(pc.getProjectCode());
            }
            summaries.sort(Comparator.comparing(RateCard.ProjectCodeSummary::projectCode));
            codeStrings.sort(String::compareTo);
            card.setProjectCodeSummaries(summaries);
            card.setProjectCodes(codeStrings);
        }
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
                        r -> new RateCardGroupKey(
                                r.customerCode(), r.projectCodesKey(), r.rateCardName(), r.effectiveFrom()),
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
                        r -> new RateCardGroupKey(
                                r.customerCode(), r.projectCodesKey(), r.rateCardName(), r.effectiveFrom()),
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
                    lines,
                    header.projectCodeIds());

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
        String projectCode = blankToNull(row.projectCode());
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
        var customerOpt = customerRepository.findByCustomerCode(customerCode);
        if (customerOpt.isEmpty()) {
            errors.add(error(row, customerCode, rateCardName,
                    "Customer Code not found: " + customerCode));
            return Optional.empty();
        }
        List<UUID> projectCodeIds = new ArrayList<>();
        String projectCodesKey = "";
        if (projectCode != null) {
            List<String> tokens = Arrays.stream(projectCode.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            List<String> sortedTokens = new ArrayList<>(tokens);
            sortedTokens.sort(String::compareTo);
            projectCodesKey = String.join(";", sortedTokens);
            for (String token : tokens) {
                var pcOpt = projectCodeRepository.findByCustomerIdAndProjectCode(
                        customerOpt.get().getId(), token);
                if (pcOpt.isEmpty()) {
                    errors.add(error(row, customerCode, rateCardName,
                            "Project Code not found for customer: " + token));
                    return Optional.empty();
                }
                projectCodeIds.add(pcOpt.get().getId());
            }
        }

        return Optional.of(new ValidatedRateCardRow(
                row.rowNumber(),
                customerCode,
                projectCodesKey,
                projectCodeIds,
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

    private record RateCardGroupKey(
            String customerCode,
            String projectCodesKey,
            String rateCardName,
            LocalDate effectiveFrom
    ) {}

    private record ValidatedRateCardRow(
            int rowNumber,
            String customerCode,
            String projectCodesKey,
            List<UUID> projectCodeIds,
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
                .map(c -> new BuCustomerRef(c.getId(), c.getCustomerCode(), c.getCustomerName(), c.isInternal()));
    }

    /**
     * Lightweight customer list for cross-module callers (ADR-008) — avoids exposing Customer entity.
     */
    public List<CustomerRef> listCustomerRefs(boolean includeInternal) {
        return findAllCustomers(includeInternal).stream()
                .map(c -> new CustomerRef(c.getId(), c.getCustomerCode(), c.getCustomerName(), c.isInternal()))
                .toList();
    }

    public Optional<CustomerRef> findCustomerRef(UUID id) {
        return customerRepository.findById(id)
                .map(c -> new CustomerRef(c.getId(), c.getCustomerCode(), c.getCustomerName(), c.isInternal()));
    }

    /** Lightweight cross-module BU identity — avoids exposing Customer entity (ADR-008). */
    public record BuCustomerRef(UUID id, String customerCode, String customerName, boolean internal) {}

    /** Lightweight cross-module customer identity for plan/revenue inputs (ADR-008). */
    public record CustomerRef(UUID id, String customerCode, String customerName, boolean internal) {}

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
