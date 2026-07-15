package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "overhead_budget",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"forecast_version_id", "plan_month", "plan_year", "overhead_line"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverheadBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_version_id", nullable = false)
    private ForecastVersion forecastVersion;

    @Column(name = "plan_month", nullable = false)
    private Integer planMonth;

    @Column(name = "plan_year", nullable = false)
    private Integer planYear;

    @Column(name = "overhead_line", nullable = false, length = 100)
    private String overheadLine;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;
}
