package com.cognologix.fpa.people.domain;

import com.cognologix.fpa.people.EmployeeRegistry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "master_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_version_id", "employee_registry_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_version_id", nullable = false)
    private PeriodVersion periodVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_registry_id", nullable = false)
    private EmployeeRegistry employeeRegistry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "people_snapshot_id")
    private PeopleSnapshot peopleSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_snapshot_id")
    private PayrollSnapshot payrollSnapshot;

    @Column(name = "practice_unit")
    private String practiceUnit;

    @Column(name = "business_unit")
    private String businessUnit;

    @Column(name = "billable_status", length = 1)
    private String billableStatus;

    @Column(name = "job_level", length = 100)
    private String jobLevel;

    @Column(name = "gross_pay", precision = 12, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "is_delivery_pu", nullable = false)
    @Builder.Default
    private boolean deliveryPu = false;

    @Column(name = "is_billable", nullable = false)
    @Builder.Default
    private boolean billable = false;

    @Column(name = "is_bench", nullable = false)
    @Builder.Default
    private boolean bench = false;

    @Column(name = "is_support", nullable = false)
    @Builder.Default
    private boolean support = false;

    @Column(name = "is_leadership", nullable = false)
    @Builder.Default
    private boolean leadership = false;

    @Column(name = "is_management", nullable = false)
    @Builder.Default
    private boolean management = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 25)
    private ReconciliationStatus reconciliationStatus;

    /** Billing client derived from project code lookup when employee is billable (ADR-029). */
    @Column(name = "billing_customer_code", length = 100)
    private String billingCustomerCode;

    /** Comma-separated data quality flags (e.g. MISSING_PROJECT_CODE). Null when no issues. */
    @Column(name = "data_quality_flags", length = 500)
    private String dataQualityFlags;

    @Column(name = "built_at", nullable = false, updatable = false)
    private Instant builtAt;

    @Column(name = "built_by")
    private String builtBy;

    @PrePersist
    private void prePersist() {
        builtAt = Instant.now();
    }
}
