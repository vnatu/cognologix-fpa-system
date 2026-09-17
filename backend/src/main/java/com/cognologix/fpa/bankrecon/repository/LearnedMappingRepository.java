package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.LearnedMapping;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearnedMappingRepository extends JpaRepository<LearnedMapping, UUID> {

    Optional<LearnedMapping> findByNormalisedNarrationAndVoucherType(
            String normalisedNarration, VoucherType voucherType);

    List<LearnedMapping> findByVoucherType(VoucherType voucherType);

    Page<LearnedMapping> findByNormalisedNarrationContainingIgnoreCaseOrLedgerNameContainingIgnoreCase(
            String narration, String ledger, Pageable pageable);
}
