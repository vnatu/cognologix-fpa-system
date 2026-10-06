package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learned_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnedMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "normalised_narration", nullable = false, columnDefinition = "TEXT")
    private String normalisedNarration;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 10)
    private VoucherType voucherType;

    @Column(name = "ledger_name", nullable = false, length = 500)
    private String ledgerName;

    @Column(name = "last_amount", precision = 14, scale = 2)
    private BigDecimal lastAmount;

    @Column(name = "use_count", nullable = false)
    @Builder.Default
    private int useCount = 1;

    @Column(name = "last_used_at", nullable = false)
    @Builder.Default
    private Instant lastUsedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private String createdBy;
}
