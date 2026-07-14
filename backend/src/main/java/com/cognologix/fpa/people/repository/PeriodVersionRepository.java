package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PeriodVersionRepository extends JpaRepository<PeriodVersion, UUID> {

    List<PeriodVersion> findByPeriodIdOrderByVersionNumberDesc(UUID periodId);

    List<PeriodVersion> findByPeriodIdAndLatestFinalisedTrue(UUID periodId);

    @Query("""
            SELECT pv FROM PeriodVersion pv
            JOIN FETCH pv.period p
            WHERE pv.status = :status
            ORDER BY p.periodYear ASC, p.periodMonth ASC, pv.versionNumber ASC
            """)
    List<PeriodVersion> findByStatusWithPeriodOrdered(@Param("status") PeriodStatus status);
}
