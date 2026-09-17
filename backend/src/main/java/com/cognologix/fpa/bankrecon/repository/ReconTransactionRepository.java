package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReconTransactionRepository extends JpaRepository<ReconTransaction, UUID> {

    List<ReconTransaction> findByRunIdOrderBySortOrderAsc(UUID runId);

    @Modifying
    @Query("delete from ReconTransaction t where t.runId = :runId")
    int deleteByRunId(@Param("runId") UUID runId);

    long countByRunId(UUID runId);
}
