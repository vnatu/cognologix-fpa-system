package com.cognologix.fpa.contracts.dto;

import com.cognologix.fpa.contracts.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record DocumentMeta(
        UUID id,
        UUID contractVersionId,
        DocumentType documentType,
        String filename,
        String contentType,
        long fileSizeBytes,
        Instant uploadedAt,
        String uploadedBy
) {}
