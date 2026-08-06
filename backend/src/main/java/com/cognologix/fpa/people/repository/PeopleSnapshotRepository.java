package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.EmployeeStatus;
import com.cognologix.fpa.people.domain.PeopleSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeopleSnapshotRepository extends JpaRepository<PeopleSnapshot, UUID> {

    List<PeopleSnapshot> findByPeriodVersionId(UUID periodVersionId);

    List<PeopleSnapshot> findBySnapshotUploadIdOrderByEmployeeIdAsc(UUID snapshotUploadId);

    Optional<PeopleSnapshot> findByPeriodVersionIdAndEmployeeId(UUID periodVersionId, String employeeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PeopleSnapshot p WHERE p.periodVersion.id = :periodVersionId")
    void deleteByPeriodVersionId(@Param("periodVersionId") UUID periodVersionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PeopleSnapshot p WHERE p.periodVersion.id = :periodVersionId"
            + " AND p.employeeStatus = :employeeStatus")
    void deleteByPeriodVersionIdAndEmployeeStatus(
            @Param("periodVersionId") UUID periodVersionId,
            @Param("employeeStatus") EmployeeStatus employeeStatus);

    long countByPeriodVersionId(UUID periodVersionId);

    long countByPeriodVersionIdAndEmployeeStatus(UUID periodVersionId, EmployeeStatus employeeStatus);
}
