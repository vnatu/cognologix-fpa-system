package com.cognologix.fpa.contracts.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateDocumentMeta(
        UUID id,
        UUID templateId,
        int versionNumber,
        String filename,
        String contentType,
        long fileSizeBytes,
        Instant uploadedAt,
        String uploadedBy
) {}
