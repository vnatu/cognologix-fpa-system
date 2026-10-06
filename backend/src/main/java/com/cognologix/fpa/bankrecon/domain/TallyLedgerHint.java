package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tally_ledger_hint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TallyLedgerHint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ledger_id", nullable = false, unique = true)
    private UUID ledgerId;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "typical_amount", length = 255)
    private String typicalAmount;

    @Column(name = "disambiguation_note", length = 500)
    private String disambiguationNote;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
