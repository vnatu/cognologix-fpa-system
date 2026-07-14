package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.MasterRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterRecordRepository extends JpaRepository<MasterRecord, UUID> {

    List<MasterRecord> findByPeriodVersionId(UUID periodVersionId);

    Optional<MasterRecord> findByPeriodVersionIdAndEmployeeRegistryId(
            UUID periodVersionId, UUID employeeRegistryId);

    Optional<MasterRecord> findByPeriodVersionIdAndPayrollSnapshotId(
            UUID periodVersionId, UUID payrollSnapshotId);

    void deleteByPeriodVersionId(UUID periodVersionId);
}
