package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Commercial terms for a customer — current value only, not effective-dated.
 *
 * Spec §7.1: DSO/payment terms are stored as a current editable value per client,
 * tracked via the standard audit log rather than a dedicated effective-dated structure.
 * Historical precision hasn't been identified as a concrete requirement yet.
 *
 * Shares the customer's PK (1:1, same UUID).
 */
@Entity
@Table(name = "commercial_terms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommercialTerms {

    @Id
    private UUID customerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "dso_days", nullable = false)
    private Integer dsoDays;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
