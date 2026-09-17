package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tally_ledger_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TallyLedgerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_name", nullable = false, unique = true)
    private String groupName;

    @Column(name = "accounting_nature", nullable = false, length = 20)
    private String accountingNature;
}
