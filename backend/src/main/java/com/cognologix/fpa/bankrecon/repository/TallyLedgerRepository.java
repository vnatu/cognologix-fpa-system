package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.TallyLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TallyLedgerRepository extends JpaRepository<TallyLedger, UUID> {

    Optional<TallyLedger> findByLedgerName(String ledgerName);

    @Query(value = """
            SELECT * FROM tally_ledger
            WHERE regexp_replace(ledger_name, E'[\\r\\n]', '', 'g') = :name
            ORDER BY CASE WHEN ledger_name = :name THEN 0 ELSE 1 END
            LIMIT 1
            """, nativeQuery = true)
    Optional<TallyLedger> findExistingForImport(@Param("name") String name);

    boolean existsByLedgerName(String ledgerName);

    List<TallyLedger> findByActiveTrueOrderByLedgerNameAsc();

    List<TallyLedger> findByActiveTrueAndBankAccountTrue();

    Page<TallyLedger> findByLedgerNameContainingIgnoreCase(String name, Pageable pageable);

    Page<TallyLedger> findByAccountingNatureIn(List<String> accountingNatures, Pageable pageable);

    Page<TallyLedger> findByAccountingNatureInAndLedgerNameContainingIgnoreCase(
            List<String> accountingNatures, String name, Pageable pageable);

    Page<TallyLedger> findByBankAccountTrue(Pageable pageable);

    Page<TallyLedger> findByBankAccountTrueAndLedgerNameContainingIgnoreCase(String name, Pageable pageable);
}
