package com.cognologix.fpa.general.repository;

import com.cognologix.fpa.general.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FxRateRepository extends JpaRepository<FxRate, UUID> {

    /** Currently active rate for a pair (effective_to is null). */
    Optional<FxRate> findByCurrencyPairAndEffectiveToIsNull(String currencyPair);

    /** Rate applicable at a specific date — required for historical revenue accuracy. */
    @Query("""
            SELECT f FROM FxRate f
            WHERE f.currencyPair = :pair
              AND f.effectiveFrom <= :asOf
              AND (f.effectiveTo IS NULL OR f.effectiveTo > :asOf)
            """)
    Optional<FxRate> findRateOnDate(@Param("pair") String currencyPair,
                                    @Param("asOf") LocalDate asOf);
}
