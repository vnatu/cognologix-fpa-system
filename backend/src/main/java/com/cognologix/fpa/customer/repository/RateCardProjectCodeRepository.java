package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.RateCardProjectCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RateCardProjectCodeRepository extends JpaRepository<RateCardProjectCode, UUID> {

    List<RateCardProjectCode> findByRateCardId(UUID rateCardId);

    List<RateCardProjectCode> findByProjectCodeId(UUID projectCodeId);

    List<RateCardProjectCode> findByRateCardIdIn(Collection<UUID> rateCardIds);

    void deleteByRateCardId(UUID rateCardId);

    /**
     * Links for active rate cards of a customer that already cover any of the given project codes.
     */
    @Query("""
            SELECT link FROM RateCardProjectCode link, RateCard rc
            WHERE link.rateCardId = rc.id
              AND rc.customer.id = :customerId
              AND rc.effectiveTo IS NULL
              AND link.projectCodeId IN :projectCodeIds
            """)
    List<RateCardProjectCode> findActiveLinksForCustomerAndProjectCodes(
            @Param("customerId") UUID customerId,
            @Param("projectCodeIds") Collection<UUID> projectCodeIds);
}
