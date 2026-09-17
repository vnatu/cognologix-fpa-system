package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.TallyLedgerGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TallyLedgerGroupRepository extends JpaRepository<TallyLedgerGroup, UUID> {

    Optional<TallyLedgerGroup> findByGroupName(String groupName);
}
