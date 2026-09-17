package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconExportRepository extends JpaRepository<ReconExport, UUID> {

    List<ReconExport> findByRunIdOrderByExportNumberAsc(UUID runId);

    int countByRunId(UUID runId);

    void deleteByRunId(UUID runId);
}
