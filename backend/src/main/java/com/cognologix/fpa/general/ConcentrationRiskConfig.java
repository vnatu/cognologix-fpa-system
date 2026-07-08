package com.cognologix.fpa.general;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Global concentration risk threshold — singleton row owned by the General module.
 *
 * Spec §8: the single-client threshold (30% default) is system-wide configuration,
 * not customer-specific. Placed here so GeneralConfigController can serve the
 * /api/general/concentration-risk-config endpoints without any cross-module cycle.
 * Combined-client watch groups remain in the customer module (they reference Customer).
 *
 * Seeded by V2 migration. Treated as singleton in the service layer.
 */
@Entity
@Table(name = "concentration_risk_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcentrationRiskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "single_client_threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal singleClientThresholdPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
}
