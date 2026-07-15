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

    Optional<RateCard> findByIdAndCustomerId(UUID id, UUID customerId);

    List<RateCard> findByCustomerIdAndEffectiveToIsNull(UUID customerId);

    /**
     * Blended card applicable on a given date — no rows in rate_card_project_code (ADR-035).
     */
    @Query("""
            SELECT r FROM RateCard r
            WHERE r.customer.id = :customerId
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
              AND NOT EXISTS (
                  SELECT 1 FROM RateCardProjectCode link WHERE link.rateCardId = r.id
              )
            """)
    Optional<RateCard> findActiveBlendedOnDate(@Param("customerId") UUID customerId,
                                               @Param("asOf") LocalDate asOf);

    /**
     * Project-scoped card covering {@code projectCodeId} on {@code asOf} (ADR-035).
     */
    @Query("""
            SELECT r FROM RateCard r
            WHERE r.customer.id = :customerId
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
              AND EXISTS (
                  SELECT 1 FROM RateCardProjectCode link
                  WHERE link.rateCardId = r.id AND link.projectCodeId = :projectCodeId
              )
            """)
    Optional<RateCard> findActiveForProjectOnDate(@Param("customerId") UUID customerId,
                                                  @Param("projectCodeId") UUID projectCodeId,
                                                  @Param("asOf") LocalDate asOf);

    @Query("""
            SELECT DISTINCT rc FROM RateCard rc
            JOIN FETCH rc.customer c
            JOIN FETCH rc.lines
            ORDER BY c.customerCode ASC, rc.effectiveFrom ASC
            """)
    List<RateCard> findAllForExport();
}
