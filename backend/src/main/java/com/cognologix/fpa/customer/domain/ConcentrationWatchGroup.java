package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Combined-client concentration watch group.
 *
 * Spec §8: supports tracking of combined client exposure (e.g. Icertis + Cadent combined)
 * with its own threshold, separate from the global single-client threshold.
 */
@Entity
@Table(name = "concentration_watch_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcentrationWatchGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal thresholdPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
            name = "concentration_watch_group_member",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "customer_id")
    )
    @Builder.Default
    private List<Customer> members = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
