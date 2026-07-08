package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.AlternateIdLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlternateIdLinkRepository extends JpaRepository<AlternateIdLink, UUID> {

    Optional<AlternateIdLink> findByAlternateEmployeeNo(String alternateEmployeeNo);
}
