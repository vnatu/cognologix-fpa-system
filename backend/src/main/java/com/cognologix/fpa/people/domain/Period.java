package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "period",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_month", "period_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
