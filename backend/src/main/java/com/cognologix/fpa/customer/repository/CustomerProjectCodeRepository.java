package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.CustomerProjectCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProjectCodeRepository extends JpaRepository<CustomerProjectCode, UUID> {
    List<CustomerProjectCode> findByCustomerId(UUID customerId);
    Optional<CustomerProjectCode> findByIdAndCustomerId(UUID id, UUID customerId);
}
