package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "client_revenue_plan",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"forecast_version_id", "customer_id", "plan_month", "plan_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientRevenuePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "forecast_version_id", nullable = false)
    private ForecastVersion forecastVersion;

    /** Soft reference to customer module — no cross-module FK (ADR-008). */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "plan_month", nullable = false)
    private Integer planMonth;

    @Column(name = "plan_year", nullable = false)
    private Integer planYear;

    @Column(name = "planned_tm_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal plannedTmRevenue = BigDecimal.ZERO;

    @Column(name = "planned_fixed_bid_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal plannedFixedBidRevenue = BigDecimal.ZERO;
}
