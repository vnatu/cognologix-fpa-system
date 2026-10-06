package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconAuditLogRepository extends JpaRepository<ReconAuditLog, UUID> {
}
