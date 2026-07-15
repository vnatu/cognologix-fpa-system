package com.cognologix.fpa.revenue.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "revenue_invoice",
        uniqueConstraints = @UniqueConstraint(columnNames = {"revenue_upload_id", "invoice_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revenue_upload_id", nullable = false)
    private RevenueUpload revenueUpload;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    /** Soft reference — customer code (cross-module, no FK). */
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance", precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private RevenueCurrency currency = RevenueCurrency.USD;

    @Column(name = "project_code", length = 100)
    private String projectCode;

    @Column(name = "amount_inr", precision = 14, scale = 2)
    private BigDecimal amountInr;

    /** ADR-017 — FX rate used for USD→INR conversion. */
    @Column(name = "fx_rate_id")
    private UUID fxRateId;
}
