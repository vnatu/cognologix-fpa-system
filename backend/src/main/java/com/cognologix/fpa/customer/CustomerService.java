package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.*;
import com.cognologix.fpa.customer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ── Customer Master ──────────────────────────────────────────────────────

    public List<Customer> findAllCustomers() {
        return customerRepository.findAll();
    }

    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
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
    public Customer updateCustomer(UUID customerId,
                                   String customerName,
                                   LifecycleStatus lifecycleStatus,
                                   String relationshipOwnerEmployeeId,
                                   Integer dsoDays) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        if (customerName != null) customer.setCustomerName(customerName);
        if (lifecycleStatus != null) customer.setLifecycleStatus(lifecycleStatus);
        if (relationshipOwnerEmployeeId != null) customer.setRelationshipOwnerEmployeeId(relationshipOwnerEmployeeId);
        if (dsoDays != null) {
            var terms = commercialTermsRepository.findById(customerId)
                    .orElse(CommercialTerms.builder().customer(customer).build());
            terms.setDsoDays(dsoDays);
            commercialTermsRepository.save(terms);
        }
        return customerRepository.save(customer);
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
        return customerRepository.existsByCustomerCodeOrCustomerName(
                customerCodeOrName, customerCodeOrName);
    }
}
