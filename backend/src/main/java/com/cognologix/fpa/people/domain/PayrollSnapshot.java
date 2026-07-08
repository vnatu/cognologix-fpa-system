package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payroll_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_version_id", "employee_no"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_upload_id", nullable = false)
    private SnapshotUpload snapshotUpload;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_version_id", nullable = false)
    private PeriodVersion periodVersion;

    @Column(name = "employee_no", nullable = false, length = 100)
    private String employeeNo;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "gross_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPay;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Column(name = "ctc_per_annum", precision = 14, scale = 2)
    private BigDecimal ctcPerAnnum;
}
