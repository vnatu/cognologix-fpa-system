package com.cognologix.fpa.revenue.dto;

import com.cognologix.fpa.people.MappingTemplateApi;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RevenueMappingDtos {

    private RevenueMappingDtos() {}

    public record MappingLineRequest(
            @NotBlank @Size(max = 255) String excelColumnName,
            @NotBlank @Size(max = 100) String systemAttribute
    ) {}

    public record CreateMappingRequest(
            @NotNull RevenueImportType importType,
            @NotBlank @Size(max = 255) String templateName,
            @NotEmpty @Valid List<MappingLineRequest> lines
    ) {}

    public record MappingLineResponse(UUID id, String excelColumnName, String systemAttribute) {}

    public record MappingTemplateResponse(
            UUID id,
            String importType,
            String templateName,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            List<MappingLineResponse> lines
    ) {
        public static MappingTemplateResponse from(MappingTemplateApi api) {
            return new MappingTemplateResponse(
                    api.id(),
                    api.importType(),
                    api.templateName(),
                    api.active(),
                    api.createdAt(),
                    api.updatedAt(),
                    api.lines().stream()
                            .map(l -> new MappingLineResponse(l.id(), l.excelColumnName(), l.systemAttribute()))
                            .toList());
        }
    }

    public record ParseHeadersResponse(List<String> headers, int rowCount) {}
}
