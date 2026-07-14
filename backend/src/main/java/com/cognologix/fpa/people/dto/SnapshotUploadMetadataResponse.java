package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.SnapshotUpload;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record SnapshotUploadMetadataResponse(
        UUID id,
        ImportType importType,
        String uploadedBy,
        Instant uploadedAt,
        String originalFilename,
        int rowCount,
        List<String> unmappedColumns,
        List<String> missingColumns,
        List<String> unrecognizedBuCodes
) {
    public static SnapshotUploadMetadataResponse from(SnapshotUpload upload) {
        return new SnapshotUploadMetadataResponse(
                upload.getId(),
                upload.getImportType(),
                upload.getUploadedBy(),
                upload.getUploadedAt(),
                upload.getOriginalFilename(),
                upload.getRowCount(),
                splitCsv(upload.getUnmappedColumns()),
                splitCsv(upload.getMissingColumns()),
                splitCsv(upload.getUnrecognizedBuCodes()));
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
