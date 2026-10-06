package com.cognologix.fpa.contracts.repository;

import com.cognologix.fpa.contracts.ContractNotificationType;
import com.cognologix.fpa.contracts.domain.ContractNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContractNotificationLogRepository extends JpaRepository<ContractNotificationLog, UUID> {

    boolean existsByContractIdAndNotificationTypeAndDaysBeforeExpiry(
            UUID contractId, ContractNotificationType notificationType, int daysBeforeExpiry);

    List<ContractNotificationLog> findByContractIdOrderBySentAtDesc(UUID contractId);
}
