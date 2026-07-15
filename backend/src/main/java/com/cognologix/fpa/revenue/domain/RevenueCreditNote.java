package com.cognologix.fpa.revenue.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "revenue_credit_note",
        uniqueConstraints = @UniqueConstraint(columnNames = {"revenue_upload_id", "credit_note_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueCreditNote {

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

    @Column(name = "credit_note_number", nullable = false, length = 100)
    private String creditNoteNumber;

    /** Soft reference — customer code (cross-module, no FK). */
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "credit_note_date")
    private LocalDate creditNoteDate;

    @Column(name = "status", length = 30)
    private String status;

    /** Stored as positive; treated as negative in net revenue calculations (ADR-040). */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private RevenueCurrency currency = RevenueCurrency.USD;

    @Column(name = "amount_inr", precision = 14, scale = 2)
    private BigDecimal amountInr;

    /** ADR-017 — FX rate used for USD→INR conversion. */
    @Column(name = "fx_rate_id")
    private UUID fxRateId;
}
