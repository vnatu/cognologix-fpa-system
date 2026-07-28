package com.cognologix.fpa.budgeting.dto;

import java.util.List;

public record PlanInputImportResponse(
        int totalRows,
        int created,
        int skipped,
        List<PlanInputImportRowError> errors
) {}
