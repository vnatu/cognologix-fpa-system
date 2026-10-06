package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.domain.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {
    List<ContractTemplate> findByActiveTrueOrderByTemplateNameAsc();
}
