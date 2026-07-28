package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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

    @Column(name = "epf_contribution", precision = 12, scale = 2)
    private BigDecimal epfContribution;

    @Column(name = "eps_contribution", precision = 12, scale = 2)
    private BigDecimal epsContribution;

    @Column(name = "edli_contribution", precision = 12, scale = 2)
    private BigDecimal edliContribution;

    @Column(name = "epf_admin_charges", precision = 12, scale = 2)
    private BigDecimal epfAdminCharges;

    @Column(name = "vpf", precision = 12, scale = 2)
    private BigDecimal vpf;

    @Column(name = "nps_deduction", precision = 12, scale = 2)
    private BigDecimal npsDeduction;

    @Column(name = "gratuity", precision = 12, scale = 2)
    private BigDecimal gratuity;

    /** DB-generated: sum of employer contribution columns (nulls treated as zero). */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "total_employer_contributions", precision = 12, scale = 2,
            insertable = false, updatable = false)
    private BigDecimal totalEmployerContributions;

    /**
     * Prefer the DB-generated column when loaded; otherwise sum components
     * (needed in the same transaction before a refresh).
     */
    public BigDecimal resolvedTotalEmployerContributions() {
        if (totalEmployerContributions != null) {
            return totalEmployerContributions;
        }
        return nz(epfContribution)
                .add(nz(epsContribution))
                .add(nz(edliContribution))
                .add(nz(epfAdminCharges))
                .add(nz(vpf))
                .add(nz(npsDeduction))
                .add(nz(gratuity));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
