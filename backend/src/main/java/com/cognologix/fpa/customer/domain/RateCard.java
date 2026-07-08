package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Effective-dated rate card header for a customer.
 *
 * Spec §6: a client has exactly one active rate card at a time (enforced by the
 * no_overlapping_rate_cards exclusion constraint in V2 migration).
 * A rate change creates a new row with a new effective_from rather than
 * overwriting the prior one — point-in-time principle from Module 1 §6.1.
 */
@Entity
@Table(name = "rate_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_card_type", nullable = false, length = 10)
    private RateCardType rateCardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private RateCurrency currency;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null means currently active. Set when a newer rate card supersedes this one. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "rateCard", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RateCardLine> lines = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
