package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.SalaryBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalaryBudgetRepository extends JpaRepository<SalaryBudget, UUID> {

    List<SalaryBudget> findByForecastVersionId(UUID forecastVersionId);

    Optional<SalaryBudget> findByForecastVersionIdAndPlanMonthAndPlanYear(
            UUID forecastVersionId, int planMonth, int planYear);

    void deleteByForecastVersionId(UUID forecastVersionId);
}
