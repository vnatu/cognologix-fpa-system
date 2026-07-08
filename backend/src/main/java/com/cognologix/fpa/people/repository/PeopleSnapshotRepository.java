package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.PeopleSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PeopleSnapshotRepository extends JpaRepository<PeopleSnapshot, UUID> {

    List<PeopleSnapshot> findByPeriodVersionId(UUID periodVersionId);
}
