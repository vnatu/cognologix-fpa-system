package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.SnapshotUpload;

import java.time.Instant;
import java.util.UUID;

public record SnapshotUploadSummary(
        UUID id,
        ImportType importType,
        String uploadedBy,
        Instant uploadedAt,
        String originalFilename,
        int rowCount
) {
    public static SnapshotUploadSummary from(SnapshotUpload u) {
        return new SnapshotUploadSummary(
                u.getId(),
                u.getImportType(),
                u.getUploadedBy(),
                u.getUploadedAt(),
                u.getOriginalFilename(),
                u.getRowCount());
    }
}
