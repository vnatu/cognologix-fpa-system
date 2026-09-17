package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ContraRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContraRuleRepository extends JpaRepository<ContraRule, UUID> {

    List<ContraRule> findByActiveTrue();
}
