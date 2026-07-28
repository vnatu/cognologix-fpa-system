package com.cognologix.fpa.people.dto;

import java.util.List;

public record SimpleImportResponse(
        int totalRows,
        int created,
        int skipped,
        List<SimpleImportRowError> errors
) {}
