package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.FinancialYearPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FinancialYearPlanRepository extends JpaRepository<FinancialYearPlan, UUID> {

    Optional<FinancialYearPlan> findByFiscalYear(String fiscalYear);

    boolean existsByFiscalYear(String fiscalYear);

    Optional<FinancialYearPlan> findByFiscalYearStartLessThanEqualAndFiscalYearEndGreaterThanEqual(
            LocalDate date, LocalDate sameDate);
}
