package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forecast_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"forecast_type_id", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_type_id", nullable = false)
    private ForecastType forecastType;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ForecastVersionStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "superseded_by")
    private String supersededBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
