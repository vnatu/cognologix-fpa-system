package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.PayrollSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollSnapshotRepository extends JpaRepository<PayrollSnapshot, UUID> {

    List<PayrollSnapshot> findByPeriodVersionId(UUID periodVersionId);

    List<PayrollSnapshot> findByPeriodVersionIdOrderByEmployeeNoAsc(UUID periodVersionId);

    List<PayrollSnapshot> findBySnapshotUploadIdOrderByEmployeeNoAsc(UUID snapshotUploadId);

    Optional<PayrollSnapshot> findByPeriodVersionIdAndEmployeeNo(UUID periodVersionId, String employeeNo);

    boolean existsByPeriodVersionIdAndImportType(UUID periodVersionId, ImportType importType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PayrollSnapshot p WHERE p.periodVersion.id = :periodVersionId")
    void deleteByPeriodVersionId(@Param("periodVersionId") UUID periodVersionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PayrollSnapshot p WHERE p.periodVersion.id = :periodVersionId AND p.importType = :importType")
    void deleteByPeriodVersionIdAndImportType(
            @Param("periodVersionId") UUID periodVersionId,
            @Param("importType") ImportType importType);

    long countByPeriodVersionId(UUID periodVersionId);
}
