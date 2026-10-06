package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.LlmHint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LlmHintRepository extends JpaRepository<LlmHint, UUID> {

    List<LlmHint> findByActiveTrue();
}
