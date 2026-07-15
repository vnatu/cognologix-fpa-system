package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "financial_year_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialYearPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fiscal_year", nullable = false, unique = true, length = 10)
    private String fiscalYear;

    @Column(name = "fiscal_year_start", nullable = false)
    private LocalDate fiscalYearStart;

    @Column(name = "fiscal_year_end", nullable = false)
    private LocalDate fiscalYearEnd;

    @Column(name = "opening_hc", nullable = false)
    @Builder.Default
    private int openingHc = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @OneToMany(mappedBy = "financialYearPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ForecastType> forecastTypes = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
