package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.TallyLedgerHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TallyLedgerHintRepository extends JpaRepository<TallyLedgerHint, UUID> {

    Optional<TallyLedgerHint> findByLedgerId(UUID ledgerId);

    @Query("SELECT h.ledgerId FROM TallyLedgerHint h")
    List<UUID> findLedgerIds();

    List<TallyLedgerHint> findByLedgerIdIn(Collection<UUID> ledgerIds);
}
