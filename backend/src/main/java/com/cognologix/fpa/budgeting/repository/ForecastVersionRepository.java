package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.ForecastVersion;
import com.cognologix.fpa.budgeting.domain.ForecastVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForecastVersionRepository extends JpaRepository<ForecastVersion, UUID> {

    List<ForecastVersion> findByForecastTypeIdOrderByVersionNumberAsc(UUID forecastTypeId);

    Optional<ForecastVersion> findByForecastTypeIdAndStatus(UUID forecastTypeId, ForecastVersionStatus status);

    Optional<ForecastVersion> findByForecastTypeIdAndVersionNumber(UUID forecastTypeId, int versionNumber);
}
