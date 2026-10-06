package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recon_export")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "export_number", nullable = false)
    private int exportNumber;

    @Column(name = "filename", nullable = false, length = 500)
    private String filename;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    @Column(name = "generated_at", nullable = false)
    @Builder.Default
    private Instant generatedAt = Instant.now();

    @Column(name = "generated_by")
    private String generatedBy;
}
