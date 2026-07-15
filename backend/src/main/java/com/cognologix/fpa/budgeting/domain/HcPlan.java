package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "hc_plan",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"forecast_version_id", "plan_month", "plan_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HcPlan {

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

    @Column(name = "planned_hires", nullable = false)
    @Builder.Default
    private int plannedHires = 0;

    @Column(name = "planned_exits", nullable = false)
    @Builder.Default
    private int plannedExits = 0;

    @Column(name = "planned_billable_hc", nullable = false)
    @Builder.Default
    private int plannedBillableHc = 0;

    @Column(name = "planned_bench_hc", nullable = false)
    @Builder.Default
    private int plannedBenchHc = 0;

    @Column(name = "planned_support_hc", nullable = false)
    @Builder.Default
    private int plannedSupportHc = 0;

    @Column(name = "planned_leadership_hc", nullable = false)
    @Builder.Default
    private int plannedLeadershipHc = 0;

    @Column(name = "planned_management_hc", nullable = false)
    @Builder.Default
    private int plannedManagementHc = 0;
}
