package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.PeriodVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PeriodVersionRepository extends JpaRepository<PeriodVersion, UUID> {

    List<PeriodVersion> findByPeriodIdOrderByVersionNumberDesc(UUID periodId);
}
