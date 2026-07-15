package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.HcPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HcPlanRepository extends JpaRepository<HcPlan, UUID> {

    List<HcPlan> findByForecastVersionId(UUID forecastVersionId);

    Optional<HcPlan> findByForecastVersionIdAndPlanMonthAndPlanYear(
            UUID forecastVersionId, int planMonth, int planYear);

    void deleteByForecastVersionId(UUID forecastVersionId);
}
