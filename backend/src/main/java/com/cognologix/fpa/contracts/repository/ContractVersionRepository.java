package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.VersionStatus;
import com.cognologix.fpa.contracts.domain.ContractVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, UUID> {

    List<ContractVersion> findByContractIdOrderByVersionNumberDesc(UUID contractId);

    Optional<ContractVersion> findFirstByContractIdAndStatusNotOrderByVersionNumberDesc(
            UUID contractId, VersionStatus status);

    boolean existsByContractIdAndStatusAndIdNot(UUID contractId, VersionStatus status, UUID id);

    boolean existsByContractIdAndStatus(UUID contractId, VersionStatus status);

    @Query("select coalesce(max(v.versionNumber), 0) from ContractVersion v where v.contractId = :contractId")
    int maxVersionNumber(UUID contractId);
}
