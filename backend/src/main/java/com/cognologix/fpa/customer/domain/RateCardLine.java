package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a rate card.
 *
 * FLAT card:   exactly one line with jobLevel = null.
 * TIERED card: one line per Job Level, keyed to Zoho People's Job Level taxonomy (spec §6).
 * Currency lives on the parent RateCard — all lines share the same currency (ADR-020).
 */
@Entity
@Table(name = "rate_card_line",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rate_card_id", "job_level"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateCardLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_card_id", nullable = false)
    private RateCard rateCard;

    /** Null for FLAT blended rate; Job Level value for TIERED. */
    @Column(name = "job_level", length = 100)
    private String jobLevel;

    @Column(name = "rate_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rateAmount;
}
