package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "salary_budget",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"forecast_version_id", "plan_month", "plan_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryBudget {

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

    @Column(name = "billable_salaries", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal billableSalaries = BigDecimal.ZERO;

    @Column(name = "bench_salaries", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal benchSalaries = BigDecimal.ZERO;

    @Column(name = "support_salaries", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal supportSalaries = BigDecimal.ZERO;

    @Column(name = "cofounders_salaries", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cofoundersSalaries = BigDecimal.ZERO;

    @Column(name = "senior_mgmt_salaries", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal seniorMgmtSalaries = BigDecimal.ZERO;
}
