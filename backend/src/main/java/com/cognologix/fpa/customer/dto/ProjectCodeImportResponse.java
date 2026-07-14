package com.cognologix.fpa.customer.dto;

import java.util.List;

public record ProjectCodeImportResponse(
        int totalRows,
        int created,
        int skipped,
        List<ProjectCodeImportRowError> errors
) {}
