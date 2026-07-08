package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.SnapshotUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SnapshotUploadRepository extends JpaRepository<SnapshotUpload, UUID> {

    List<SnapshotUpload> findByPeriodVersionId(UUID periodVersionId);
}
