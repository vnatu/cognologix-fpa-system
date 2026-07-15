package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.ForecastType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForecastTypeRepository extends JpaRepository<ForecastType, UUID> {

    List<ForecastType> findByFinancialYearPlanId(UUID financialYearPlanId);

    Optional<ForecastType> findByFinancialYearPlanIdAndTypeName(UUID financialYearPlanId, String typeName);

    Optional<ForecastType> findByFinancialYearPlanIdAndPrimaryTrue(UUID financialYearPlanId);
}
