package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.ClientRevenuePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRevenuePlanRepository extends JpaRepository<ClientRevenuePlan, UUID> {

    List<ClientRevenuePlan> findByForecastVersionId(UUID forecastVersionId);

    List<ClientRevenuePlan> findByForecastVersionIdAndPlanMonthAndPlanYear(
            UUID forecastVersionId, int planMonth, int planYear);

    void deleteByForecastVersionId(UUID forecastVersionId);
}
