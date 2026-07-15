package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.OverheadBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OverheadBudgetRepository extends JpaRepository<OverheadBudget, UUID> {

    List<OverheadBudget> findByForecastVersionId(UUID forecastVersionId);

    List<OverheadBudget> findByForecastVersionIdAndPlanMonthAndPlanYear(
            UUID forecastVersionId, int planMonth, int planYear);

    void deleteByForecastVersionId(UUID forecastVersionId);
}
