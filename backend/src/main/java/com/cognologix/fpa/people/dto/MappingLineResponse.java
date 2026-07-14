package com.cognologix.fpa.people.dto;

import java.util.UUID;

public record MappingLineResponse(
        UUID id,
        String excelColumnName,
        String systemAttribute
) {}
