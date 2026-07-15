package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_revenue_actual",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"financial_year_plan_id", "customer_id", "actuals_month", "actuals_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientRevenueActual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_plan_id", nullable = false)
    private FinancialYearPlan financialYearPlan;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "actuals_month", nullable = false)
    private Integer actualsMonth;

    @Column(name = "actuals_year", nullable = false)
    private Integer actualsYear;

    @Column(name = "actual_revenue", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal actualRevenue = BigDecimal.ZERO;

    @Column(name = "entered_by")
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @PrePersist
    private void prePersist() {
        if (enteredAt == null) {
            enteredAt = Instant.now();
        }
    }
}
