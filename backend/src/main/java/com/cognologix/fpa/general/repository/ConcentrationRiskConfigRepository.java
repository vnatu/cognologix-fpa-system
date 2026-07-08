package com.cognologix.fpa.general.repository;

import com.cognologix.fpa.general.ConcentrationRiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConcentrationRiskConfigRepository extends JpaRepository<ConcentrationRiskConfig, UUID> {
}
