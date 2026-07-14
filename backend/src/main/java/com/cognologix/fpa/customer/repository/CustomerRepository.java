package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.Customer;
import com.cognologix.fpa.customer.domain.LifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCustomerCode(String customerCode);

    List<Customer> findByCustomerCodeIn(Collection<String> customerCodes);

    Optional<Customer> findByZohoBooksCustomerRef(String zohoBooksCustomerRef);

    List<Customer> findByLifecycleStatus(LifecycleStatus status);

    boolean existsByCustomerCode(String customerCode);

    boolean existsByCustomerCodeAndInternalTrue(String customerCode);

    List<Customer> findByInternalFalse();

    List<Customer> findByInternalTrue();

    /** Used by People & Payroll (via CustomerService) to validate a BU value against the Customer Master. */
    boolean existsByCustomerCodeOrCustomerName(String customerCode, String customerName);

    /** Resolves a Zoho People BU value (code or display name) to a Customer Master record. */
    Optional<Customer> findByCustomerCodeOrCustomerName(String customerCode, String customerName);
}
