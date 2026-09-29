package com.cognologix.fpa.contracts.domain;

import com.cognologix.fpa.contracts.VersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_version")
@Getter
@Setter
@NoArgsConstructor
public class ContractVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "version_label", nullable = false, length = 100)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VersionStatus status = VersionStatus.DRAFT;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;
}
