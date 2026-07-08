package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.Period;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeriodRepository extends JpaRepository<Period, UUID> {

    Optional<Period> findByPeriodMonthAndPeriodYear(int periodMonth, int periodYear);
}
