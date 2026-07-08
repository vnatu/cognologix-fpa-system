package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.PayrollSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayrollSnapshotRepository extends JpaRepository<PayrollSnapshot, UUID> {

    List<PayrollSnapshot> findByPeriodVersionId(UUID periodVersionId);
}
