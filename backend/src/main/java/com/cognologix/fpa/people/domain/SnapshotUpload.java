package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "snapshot_upload")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_version_id", nullable = false)
    private PeriodVersion periodVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 30)
    private ImportType importType;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    /** Comma-separated Excel headers present in the file but not in the mapping template. */
    @Column(name = "unmapped_columns", columnDefinition = "TEXT")
    private String unmappedColumns;

    /** Comma-separated mapping excel_column_names not found in the uploaded file. */
    @Column(name = "missing_columns", columnDefinition = "TEXT")
    private String missingColumns;

    /** Comma-separated BU codes / names not recognised by Customer Management. */
    @Column(name = "unrecognized_bu_codes", columnDefinition = "TEXT")
    private String unrecognizedBuCodes;

    @PrePersist
    private void prePersist() {
        uploadedAt = Instant.now();
    }
}
