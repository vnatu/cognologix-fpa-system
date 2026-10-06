package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recon_account_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconAccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_type", nullable = false, length = 20)
    private StatementType statementType;

    @Column(name = "identifier", nullable = false, length = 100)
    private String identifier;

    @Column(name = "ledger_name", nullable = false, length = 500)
    private String ledgerName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private String createdBy;
}
