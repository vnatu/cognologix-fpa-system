package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.OverheadActuals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OverheadActualsRepository extends JpaRepository<OverheadActuals, UUID> {

    List<OverheadActuals> findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
            UUID financialYearPlanId, int actualsMonth, int actualsYear);

    List<OverheadActuals> findByFinancialYearPlanId(UUID financialYearPlanId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM OverheadActuals o
            WHERE o.financialYearPlan.id = :planId
              AND o.actualsMonth = :month
              AND o.actualsYear = :year
            """)
    void deleteByPlanMonthYear(
            @Param("planId") UUID planId, @Param("month") int month, @Param("year") int year);
}
