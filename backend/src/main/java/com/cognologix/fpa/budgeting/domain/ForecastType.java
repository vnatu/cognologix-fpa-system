package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "forecast_type",
        uniqueConstraints = @UniqueConstraint(columnNames = {"financial_year_plan_id", "type_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastType {

    public static final String NORMAL = "NORMAL";
    public static final String AGGRESSIVE = "AGGRESSIVE";
    public static final String CONSERVATIVE = "CONSERVATIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_plan_id", nullable = false)
    private FinancialYearPlan financialYearPlan;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "forecastType", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ForecastVersion> versions = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
