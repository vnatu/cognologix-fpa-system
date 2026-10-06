package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ReconRunRepository extends JpaRepository<ReconRun, UUID> {

    Optional<ReconRun> findByRunNumber(String runNumber);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(run_number FROM 3) AS INTEGER)), 0) FROM recon_run",
            nativeQuery = true)
    int findMaxRunSequence();
}
