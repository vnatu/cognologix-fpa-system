package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recon_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "transaction_date")
    private Instant transactionDate;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "normalised_description", nullable = false, columnDefinition = "TEXT")
    private String normalisedDescription;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_credit", nullable = false, length = 1)
    private DebitCredit debitCredit;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "transaction_branch", length = 100)
    private String transactionBranch;

    @Column(name = "running_balance", precision = 14, scale = 2)
    private BigDecimal runningBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 10)
    private VoucherType voucherType;

    @Column(name = "mapped_ledger", length = 500)
    private String mappedLedger;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_source", length = 10)
    private MappingSource mappingSource;

    @Column(name = "is_excluded", nullable = false)
    @Builder.Default
    private boolean excluded = false;

    @Column(name = "is_reviewed", nullable = false)
    @Builder.Default
    private boolean reviewed = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /** EXACT or RESOLVED when the model name was accepted. */
    @Column(name = "llm_name_resolution", length = 16)
    private String llmNameResolution;

    /** AMBIGUOUS when several catalog ledgers match the returned name. */
    @Column(name = "review_tag", length = 32)
    private String reviewTag;

    /** Matched code word and its position, or a note that two codes conflicted. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
