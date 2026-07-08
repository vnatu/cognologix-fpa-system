package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.ConcentrationWatchGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConcentrationWatchGroupRepository extends JpaRepository<ConcentrationWatchGroup, UUID> {
}
