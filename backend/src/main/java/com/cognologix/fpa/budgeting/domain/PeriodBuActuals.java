package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "period_bu_actuals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_actuals_id", "business_unit"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodBuActuals {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_actuals_id", nullable = false)
    private PeriodActuals periodActuals;

    @Column(name = "business_unit", nullable = false)
    private String businessUnit;

    @Column(name = "billable_hc", nullable = false)
    @Builder.Default
    private int billableHc = 0;

    @Column(name = "total_gross_pay", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalGrossPay = BigDecimal.ZERO;
}
