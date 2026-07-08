package com.cognologix.fpa.general;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * System-wide FX rate, effective-dated per currency pair (ADR-017).
 *
 * Placed in the general module root package so it is part of the public API —
 * modules that receive FxRate from GeneralConfigService can import this type
 * without violating Spring Modulith boundaries.
 *
 * Overlap exclusion (no_overlapping_fx_rates) is enforced by the DB constraint in V3.
 * Null effective_to means currently active.
 */
@Entity
@Table(name = "fx_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. "USD_INR" */
    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(name = "rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null = currently active. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
