package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payroll_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "payroll_snapshot_period_version_employee_import_type_key",
                columnNames = {"period_version_id", "employee_no", "import_type"}))
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

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 30)
    @Builder.Default
    private ImportType importType = ImportType.ZOHO_PAYROLL;

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
