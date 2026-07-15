package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Join between rate card and customer project code (ADR-035 / V17 many-to-many).
 * Soft UUID references — no JPA associations.
 */
@Entity
@Table(name = "rate_card_project_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateCardProjectCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rate_card_id", nullable = false)
    private UUID rateCardId;

    @Column(name = "project_code_id", nullable = false)
    private UUID projectCodeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
