package com.cognologix.fpa.budgeting.dto;

public record PlanInputImportRowError(
        int rowNumber,
        String reason
) {}
