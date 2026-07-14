package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.SnapshotUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotUploadRepository extends JpaRepository<SnapshotUpload, UUID> {

    List<SnapshotUpload> findByPeriodVersionId(UUID periodVersionId);

    boolean existsByPeriodVersionIdAndImportType(UUID periodVersionId, ImportType importType);

    List<SnapshotUpload> findByPeriodVersionIdOrderByUploadedAtDesc(UUID periodVersionId);

    Optional<SnapshotUpload> findFirstByPeriodVersionIdAndImportTypeOrderByUploadedAtDesc(
            UUID periodVersionId, ImportType importType);
}
