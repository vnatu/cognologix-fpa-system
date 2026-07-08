package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.RateCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RateCardRepository extends JpaRepository<RateCard, UUID> {

    List<RateCard> findByCustomerIdOrderByEffectiveFromDesc(UUID customerId);

    /** Active rate card = effective_to is null (currently open). */
    Optional<RateCard> findByCustomerIdAndEffectiveToIsNull(UUID customerId);

    /** Rate card applicable for a given point in time — used by Revenue module for historical accuracy. */
    @Query("""
            SELECT r FROM RateCard r
            WHERE r.customer.id = :customerId
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo > :asOf)
            """)
    Optional<RateCard> findActiveOnDate(@Param("customerId") UUID customerId,
                                        @Param("asOf") LocalDate asOf);
}
