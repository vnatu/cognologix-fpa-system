package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.PeriodBuActuals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PeriodBuActualsRepository extends JpaRepository<PeriodBuActuals, UUID> {

    List<PeriodBuActuals> findByPeriodActualsId(UUID periodActualsId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PeriodBuActuals b WHERE b.periodActuals.id = :periodActualsId")
    void deleteByPeriodActualsId(@Param("periodActualsId") UUID periodActualsId);
}
