package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "period_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_id", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private Period period;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PeriodStatus status;

    @Column(name = "is_latest_finalised", nullable = false)
    @Builder.Default
    private boolean latestFinalised = false;

    @Column(name = "finalised_at")
    private Instant finalisedAt;

    @Column(name = "finalised_by")
    private String finalisedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
