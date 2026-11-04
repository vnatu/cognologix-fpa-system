package com.cognologix.fpa.bankrecon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "recon_card_reference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconCardReference {

    @Id
    @Column(name = "bank_reference", nullable = false, length = 255)
    private String bankReference;

    @Column(name = "run_id", nullable = false)
    private UUID runId;
}
