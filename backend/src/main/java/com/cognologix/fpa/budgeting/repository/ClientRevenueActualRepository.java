package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.ClientRevenueActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientRevenueActualRepository extends JpaRepository<ClientRevenueActual, UUID> {

    List<ClientRevenueActual> findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
            UUID financialYearPlanId, int actualsMonth, int actualsYear);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ClientRevenueActual c
            WHERE c.financialYearPlan.id = :planId
              AND c.actualsMonth = :month
              AND c.actualsYear = :year
            """)
    void deleteByPlanMonthYear(
            @Param("planId") UUID planId, @Param("month") int month, @Param("year") int year);
}
