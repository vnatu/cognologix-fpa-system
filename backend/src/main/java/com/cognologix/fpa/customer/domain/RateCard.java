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
 * ADR-035 / V17: project associations live in {@code rate_card_project_code}.
 * Empty associations = customer-level blended card. Soft list of project codes
 * for display is populated on read.
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

    /**
     * Display only — project code strings associated via rate_card_project_code,
     * populated on read.
     */
    @Transient
    @Builder.Default
    private List<String> projectCodes = new ArrayList<>();

    /**
     * Display only — full project code summaries for API responses, populated on read.
     */
    @Transient
    @Builder.Default
    private List<ProjectCodeSummary> projectCodeSummaries = new ArrayList<>();

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

    /** Lightweight display DTO embedded transiently on RateCard (not a JPA entity). */
    public record ProjectCodeSummary(UUID id, String projectCode, String description) {}
}
