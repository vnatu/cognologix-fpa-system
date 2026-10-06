package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.ContractStatus;
import com.cognologix.fpa.contracts.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {

    @Query(value = "SELECT nextval('contract_number_seq')", nativeQuery = true)
    Object nextContractNumber();

    List<Contract> findByStatusAndEvergreenFalseAndExpiryDateBetweenOrderByExpiryDateAsc(
            ContractStatus status, LocalDate from, LocalDate to);

    List<Contract> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since);

    @Query("""
            select c from Contract c
            where c.status = com.cognologix.fpa.contracts.ContractStatus.ACTIVE
              and c.evergreen = false
              and c.expiryDate is not null
            """)
    List<Contract> findExpiryCandidates();

    @Query("""
            select t.displayName, count(c)
            from Contract c join c.contractType t
            group by t.displayName
            order by t.displayName
            """)
    List<Object[]> countByTypeName();

    @Query("""
            select c.status, count(c)
            from Contract c
            group by c.status
            """)
    List<Object[]> countByStatus();
}
