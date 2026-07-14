package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.CustomerProjectCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProjectCodeRepository extends JpaRepository<CustomerProjectCode, UUID> {
    List<CustomerProjectCode> findByCustomerId(UUID customerId);
    Optional<CustomerProjectCode> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<CustomerProjectCode> findByCustomerIdAndProjectCode(UUID customerId, String projectCode);

    @Query("""
            SELECT pc FROM CustomerProjectCode pc
            JOIN FETCH pc.customer c
            ORDER BY c.customerCode ASC, pc.projectCode ASC
            """)
    List<CustomerProjectCode> findAllForExport();

    @Query("""
            SELECT pc FROM CustomerProjectCode pc
            JOIN FETCH pc.customer
            WHERE pc.projectCode = :projectCode
            """)
    Optional<CustomerProjectCode> findByProjectCode(@Param("projectCode") String projectCode);
}
