package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconAccountMapping;
import com.cognologix.fpa.bankrecon.domain.StatementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconAccountMappingRepository extends JpaRepository<ReconAccountMapping, UUID> {

    List<ReconAccountMapping> findAllByOrderByStatementTypeAscIdentifierAsc();

    Optional<ReconAccountMapping> findByStatementTypeAndIdentifierAndActiveTrue(
            StatementType statementType, String identifier);

    boolean existsByStatementTypeAndIdentifier(StatementType statementType, String identifier);
}
