package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "period_actuals",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"financial_year_plan_id", "actuals_month", "actuals_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodActuals {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_plan_id", nullable = false)
    private FinancialYearPlan financialYearPlan;

    @Column(name = "actuals_month", nullable = false)
    private Integer actualsMonth;

    @Column(name = "actuals_year", nullable = false)
    private Integer actualsYear;

    @Column(name = "actual_billable_hc")
    private Integer actualBillableHc;

    @Column(name = "actual_bench_hc")
    private Integer actualBenchHc;

    @Column(name = "actual_support_hc")
    private Integer actualSupportHc;

    @Column(name = "actual_leadership_hc")
    private Integer actualLeadershipHc;

    @Column(name = "actual_management_hc")
    private Integer actualManagementHc;

    @Column(name = "actual_total_hc")
    private Integer actualTotalHc;

    @Column(name = "actual_billable_salaries", precision = 12, scale = 2)
    private BigDecimal actualBillableSalaries;

    @Column(name = "actual_bench_salaries", precision = 12, scale = 2)
    private BigDecimal actualBenchSalaries;

    @Column(name = "actual_support_salaries", precision = 12, scale = 2)
    private BigDecimal actualSupportSalaries;

    @Column(name = "actual_leadership_salaries", precision = 12, scale = 2)
    private BigDecimal actualLeadershipSalaries;

    @Column(name = "actual_management_salaries", precision = 12, scale = 2)
    private BigDecimal actualManagementSalaries;

    @Column(name = "actual_revenue_manual", precision = 12, scale = 2)
    private BigDecimal actualRevenueManual;

    /** Soft ref to people period_version (ADR-022). */
    @Column(name = "people_period_version_id")
    private UUID peoplePeriodVersionId;

    /** ADR-017: fx_rate_id for any USD→INR conversion on this snapshot. */
    @Column(name = "fx_rate_id")
    private UUID fxRateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
    }
}
