package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportColumnMapping;
import com.cognologix.fpa.people.domain.ImportType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MappingTemplateResponse(
        UUID id,
        ImportType importType,
        String templateName,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<MappingLineResponse> lines
) {
    public static MappingTemplateResponse from(ImportColumnMapping m) {
        return new MappingTemplateResponse(
                m.getId(),
                m.getImportType(),
                m.getTemplateName(),
                m.isActive(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getLines().stream()
                        .map(l -> new MappingLineResponse(l.getId(), l.getExcelColumnName(), l.getSystemAttribute()))
                        .toList());
    }
}
