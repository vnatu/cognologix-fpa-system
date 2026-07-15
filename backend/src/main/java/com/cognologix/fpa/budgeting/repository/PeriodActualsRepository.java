package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.PeriodActuals;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodActualsRepository extends JpaRepository<PeriodActuals, UUID> {

    Optional<PeriodActuals> findByFinancialYearPlanIdAndActualsMonthAndActualsYear(
            UUID financialYearPlanId, int actualsMonth, int actualsYear);

    List<PeriodActuals> findByFinancialYearPlanId(UUID financialYearPlanId);
}
