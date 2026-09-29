package com.cognologix.fpa.contracts.domain;

import com.cognologix.fpa.contracts.ContractNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_notification_log")
@Getter
@Setter
@NoArgsConstructor
public class ContractNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 10)
    private ContractNotificationType notificationType;

    @Column(name = "days_before_expiry", nullable = false)
    private int daysBeforeExpiry;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(columnDefinition = "text")
    private String recipients;
}
