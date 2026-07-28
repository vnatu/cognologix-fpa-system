package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.*;
import com.cognologix.fpa.customer.repository.*;
import com.cognologix.fpa.general.BackupGridHelper;
import com.cognologix.fpa.general.BackupSheet;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.cognologix.fpa.general.BackupGridHelper.*;

/**
 * Backup/restore grid operations for Customer Management (ADR-044 Tier 2).
 */
@Component
@RequiredArgsConstructor
class CustomerModuleBackup {

    static final String FILE_CUSTOMERS = "customers.xlsx";
    static final String FILE_RATE_CARDS = "rate_cards.xlsx";
    static final String FILE_PROJECT_CODES = "project_codes.xlsx";

    static final String[] CUSTOMER_HEADERS = {
            "customer_code", "customer_name", "zoho_books_customer_ref", "lifecycle_status",
            "is_internal", "relationship_owner_employee_id", "dso_days"
    };

    static final String[] RATE_CARD_HEADERS = {
            "customer_code", "project_code", "rate_card_name", "rate_card_type", "currency",
            "effective_from", "effective_to", "job_level", "rate_amount"
    };

    static final String[] PROJECT_CODE_HEADERS = {
            "customer_code", "project_code", "description"
    };

    private final CustomerRepository customerRepository;
    private final CustomerProjectCodeRepository projectCodeRepository;
    private final RateCardRepository rateCardRepository;
    private final RateCardProjectCodeRepository rateCardProjectCodeRepository;
    private final CommercialTermsRepository commercialTermsRepository;

    List<BackupSheet> exportBackupSheets() {
        return List.of(exportCustomersSheet(), exportRateCardsSheet(), exportProjectCodesSheet());
    }

    BackupSheet exportCustomersSheet() {
        List<Customer> customers = customerRepository.findAll().stream()
                .sorted(Comparator.comparing(Customer::getCustomerCode))
                .toList();
        Map<UUID, Integer> dsoByCustomerId = commercialTermsRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getCustomer().getId(), CommercialTerms::getDsoDays));

        List<String[]> rows = new ArrayList<>();
        for (Customer c : customers) {
            rows.add(row(
                    c.getCustomerCode(),
                    c.getCustomerName(),
                    str(c.getZohoBooksCustomerRef()),
                    c.getLifecycleStatus().name(),
                    String.valueOf(c.isInternal()),
                    str(c.getRelationshipOwnerEmployeeId()),
                    str(dsoByCustomerId.getOrDefault(c.getId(), 0))));
        }
        return new BackupSheet(FILE_CUSTOMERS, CUSTOMER_HEADERS, rows);
    }

    BackupSheet exportRateCardsSheet() {
        List<RateCard> cards = rateCardRepository.findAllForExport();
        cards.forEach(card -> {
            Hibernate.initialize(card.getCustomer());
            Hibernate.initialize(card.getLines());
        });
        Map<UUID, String> projectCodeById = projectCodeRepository.findAll().stream()
                .collect(Collectors.toMap(CustomerProjectCode::getId, CustomerProjectCode::getProjectCode));
        Map<UUID, List<UUID>> projectIdsByRateCard = rateCardProjectCodeRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        RateCardProjectCode::getRateCardId,
                        Collectors.mapping(RateCardProjectCode::getProjectCodeId, Collectors.toList())));
        cards.sort(Comparator
                .comparing((RateCard rc) -> rc.getCustomer().getCustomerCode())
                .thenComparing(RateCard::getEffectiveFrom));

        List<String[]> rows = new ArrayList<>();
        for (RateCard card : cards) {
            List<RateCardLine> lines = card.getLines().stream()
                    .sorted(Comparator.comparing(l -> l.getJobLevel() == null ? "" : l.getJobLevel()))
                    .toList();
            List<String> codes = projectIdsByRateCard.getOrDefault(card.getId(), List.of()).stream()
                    .map(projectCodeById::get)
                    .filter(c -> c != null && !c.isBlank())
                    .sorted()
                    .toList();
            String projectCodeCell = codes.isEmpty() ? "" : String.join(";", codes);
            for (RateCardLine line : lines) {
                rows.add(row(
                        card.getCustomer().getCustomerCode(),
                        projectCodeCell,
                        card.getName(),
                        card.getRateCardType().name(),
                        card.getCurrency().name(),
                        card.getEffectiveFrom().toString(),
                        card.getEffectiveTo() != null ? card.getEffectiveTo().toString() : "",
                        str(line.getJobLevel()),
                        line.getRateAmount() != null ? line.getRateAmount().toPlainString() : ""));
            }
        }
        return new BackupSheet(FILE_RATE_CARDS, RATE_CARD_HEADERS, rows);
    }

    BackupSheet exportProjectCodesSheet() {
        List<CustomerProjectCode> codes = projectCodeRepository.findAllForExport();
        codes.forEach(pc -> Hibernate.initialize(pc.getCustomer()));
        List<String[]> rows = new ArrayList<>();
        for (CustomerProjectCode pc : codes) {
            rows.add(row(
                    pc.getCustomer().getCustomerCode(),
                    pc.getProjectCode(),
                    str(pc.getDescription())));
        }
        return new BackupSheet(FILE_PROJECT_CODES, PROJECT_CODE_HEADERS, rows);
    }

    @Transactional
    void wipeCustomerData() {
        rateCardProjectCodeRepository.deleteAllInBatch();
        rateCardRepository.deleteAllInBatch();
        commercialTermsRepository.deleteAllInBatch();
        projectCodeRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @Transactional
    Map<String, Integer> restoreBackupSheets(Map<String, List<String[]>> rowsByFile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(FILE_CUSTOMERS, restoreCustomers(rowsByFile.getOrDefault(FILE_CUSTOMERS, List.of())));
        counts.put(FILE_PROJECT_CODES, restoreProjectCodes(rowsByFile.getOrDefault(FILE_PROJECT_CODES, List.of())));
        counts.put(FILE_RATE_CARDS, restoreRateCards(rowsByFile.getOrDefault(FILE_RATE_CARDS, List.of())));
        return counts;
    }

    private int restoreCustomers(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                String code = requireCell(row, 0, "customer_code");
                String name = requireCell(row, 1, "customer_name");
                String zohoRef = cell(row, 2);
                LifecycleStatus status = LifecycleStatus.valueOf(requireCell(row, 3, "lifecycle_status"));
                boolean internal = parseBoolean(cell(row, 4));
                String owner = cell(row, 5);
                int dso = parseIntRequired(cell(row, 6), "dso_days");

                Customer customer = Customer.builder()
                        .customerCode(code)
                        .customerName(name)
                        .zohoBooksCustomerRef(zohoRef)
                        .lifecycleStatus(status)
                        .internal(internal)
                        .relationshipOwnerEmployeeId(owner)
                        .build();
                customer = customerRepository.save(customer);
                commercialTermsRepository.save(CommercialTerms.builder().customer(customer).dsoDays(dso).build());
                count++;
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }
        return count;
    }

    private int restoreProjectCodes(List<String[]> rows) {
        int count = 0;
        for (String[] row : rows) {
            try {
                String customerCode = requireCell(row, 0, "customer_code");
                String projectCode = requireCell(row, 1, "project_code");
                String description = cell(row, 2);
                Customer customer = customerRepository.findByCustomerCode(customerCode).orElse(null);
                if (customer == null) {
                    continue;
                }
                projectCodeRepository.save(CustomerProjectCode.builder()
                        .customer(customer)
                        .projectCode(projectCode)
                        .description(description)
                        .build());
                count++;
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }
        return count;
    }

    private int restoreRateCards(List<String[]> rows) {
        record GroupKey(String customerCode, String projectCodesKey, String name,
                        RateCardType type, RateCurrency currency,
                        LocalDate effectiveFrom, LocalDate effectiveTo) {}

        Map<GroupKey, List<String[]>> groups = new LinkedHashMap<>();
        for (String[] row : rows) {
            try {
                String customerCode = requireCell(row, 0, "customer_code");
                String projectCode = cell(row, 1);
                String name = requireCell(row, 2, "rate_card_name");
                RateCardType type = RateCardType.valueOf(requireCell(row, 3, "rate_card_type"));
                RateCurrency currency = RateCurrency.valueOf(requireCell(row, 4, "currency"));
                LocalDate effectiveFrom = parseDate(requireCell(row, 5, "effective_from"), "effective_from");
                LocalDate effectiveTo = parseDate(cell(row, 6), "effective_to");
                String projectKey = projectCode == null ? "" : projectCode;
                GroupKey key = new GroupKey(customerCode, projectKey, name, type, currency, effectiveFrom, effectiveTo);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            } catch (RuntimeException ignored) {
                // skip bad rows
            }
        }

        int count = 0;
        for (var entry : groups.entrySet()) {
            try {
                GroupKey key = entry.getKey();
                Customer customer = customerRepository.findByCustomerCode(key.customerCode()).orElse(null);
                if (customer == null) {
                    continue;
                }
                List<RateCardLine> lines = new ArrayList<>();
                for (String[] lineRow : entry.getValue()) {
                    String jobLevel = cell(lineRow, 7);
                    BigDecimal amount = parseDecimalRequired(cell(lineRow, 8), "rate_amount");
                    lines.add(RateCardLine.builder().jobLevel(jobLevel).rateAmount(amount).build());
                }
                RateCard card = RateCard.builder()
                        .customer(customer)
                        .name(key.name())
                        .rateCardType(key.type())
                        .currency(key.currency())
                        .effectiveFrom(key.effectiveFrom())
                        .effectiveTo(key.effectiveTo())
                        .lines(lines)
                        .build();
                lines.forEach(l -> l.setRateCard(card));
                RateCard saved = rateCardRepository.save(card);

                if (key.projectCodesKey() != null && !key.projectCodesKey().isBlank()) {
                    List<UUID> projectIds = new ArrayList<>();
                    for (String code : key.projectCodesKey().split(";")) {
                        String trimmed = code.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        projectCodeRepository.findByCustomerIdAndProjectCode(customer.getId(), trimmed)
                                .ifPresent(pc -> projectIds.add(pc.getId()));
                    }
                    for (UUID projectId : projectIds) {
                        rateCardProjectCodeRepository.save(RateCardProjectCode.builder()
                                .rateCardId(saved.getId())
                                .projectCodeId(projectId)
                                .build());
                    }
                }
                count++;
            } catch (RuntimeException ignored) {
                // skip bad groups
            }
        }
        return count;
    }
}
