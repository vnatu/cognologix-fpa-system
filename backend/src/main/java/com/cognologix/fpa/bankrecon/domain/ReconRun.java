package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recon_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_number", nullable = false, unique = true, length = 20)
    private String runNumber;

    @Column(name = "statement_number", length = 100)
    private String statementNumber;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "customer_name", length = 500)
    private String customerName;

    @Column(name = "statement_period_start")
    private LocalDate statementPeriodStart;

    @Column(name = "statement_period_end")
    private LocalDate statementPeriodEnd;

    @Column(name = "opening_balance", precision = 14, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 14, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private int totalTransactions = 0;

    @Column(name = "mapped_count", nullable = false)
    @Builder.Default
    private int mappedCount = 0;

    @Column(name = "unmapped_count", nullable = false)
    @Builder.Default
    private int unmappedCount = 0;

    @Column(name = "excluded_count", nullable = false)
    @Builder.Default
    private int excludedCount = 0;

    @Column(name = "export_count", nullable = false)
    @Builder.Default
    private int exportCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private ReconRunStatus status = ReconRunStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private String createdBy;
}
