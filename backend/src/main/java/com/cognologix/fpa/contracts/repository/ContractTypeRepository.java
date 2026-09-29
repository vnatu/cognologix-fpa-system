package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.domain.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTypeRepository extends JpaRepository<ContractType, UUID> {
    List<ContractType> findByActiveTrueOrderByDisplayNameAsc();

    List<ContractType> findAllByOrderByDisplayNameAsc();

    Optional<ContractType> findByTypeCodeIgnoreCase(String typeCode);
}
