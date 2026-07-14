package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.ImportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMappingRequest(
        @NotNull ImportType importType,
        @NotBlank @Size(max = 255) String templateName,
        @NotEmpty @Valid List<MappingLineRequest> lines
) {}
