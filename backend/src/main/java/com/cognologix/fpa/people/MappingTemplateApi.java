package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ImportColumnMapping;
import com.cognologix.fpa.people.domain.ImportType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public mapping-template view for cross-module callers (Revenue — ADR-039).
 * Lives in the people root package so other modules need not import people.domain.
 */
public record MappingTemplateApi(
        UUID id,
        String importType,
        String templateName,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<MappingLineApi> lines
) {
    public record MappingLineApi(UUID id, String excelColumnName, String systemAttribute) {}

    static MappingTemplateApi from(ImportColumnMapping m) {
        return new MappingTemplateApi(
                m.getId(),
                m.getImportType().name(),
                m.getTemplateName(),
                m.isActive(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getLines().stream()
                        .map(l -> new MappingLineApi(l.getId(), l.getExcelColumnName(), l.getSystemAttribute()))
                        .toList());
    }

    static ImportType requireKnownType(String importType) {
        try {
            return ImportType.valueOf(importType);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown import type: " + importType);
        }
    }
}
