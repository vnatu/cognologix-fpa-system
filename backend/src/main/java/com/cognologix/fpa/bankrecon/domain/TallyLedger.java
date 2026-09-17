package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tally_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TallyLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ledger_name", nullable = false, unique = true, length = 500)
    private String ledgerName;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "accounting_nature", nullable = false, length = 20)
    private String accountingNature;

    @Column(name = "is_bank_account", nullable = false)
    @Builder.Default
    private boolean bankAccount = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
