package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.MasterRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MasterRecordRepository extends JpaRepository<MasterRecord, UUID> {

    List<MasterRecord> findByPeriodVersionId(UUID periodVersionId);
}
