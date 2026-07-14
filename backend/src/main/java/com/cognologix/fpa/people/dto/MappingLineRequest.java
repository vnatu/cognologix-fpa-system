package com.cognologix.fpa.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MappingLineRequest(
        @NotBlank @Size(max = 255) String excelColumnName,
        @NotBlank @Size(max = 100) String systemAttribute
) {}
