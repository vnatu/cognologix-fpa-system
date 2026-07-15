package com.cognologix.fpa.revenue.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revenue_upload",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"import_type", "period_month", "period_year", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 30)
    private RevenueImportType importType;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private RevenueUploadStatus status = RevenueUploadStatus.ACTIVE;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "unmapped_columns", columnDefinition = "TEXT")
    private String unmappedColumns;

    @Column(name = "missing_columns", columnDefinition = "TEXT")
    private String missingColumns;

    @Column(name = "unrecognized_customer_codes", columnDefinition = "TEXT")
    private String unrecognizedCustomerCodes;

    @PrePersist
    private void prePersist() {
        uploadedAt = Instant.now();
    }
}
