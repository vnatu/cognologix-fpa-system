package com.cognologix.fpa.general.dto;

public record FxRateImportRowError(
        int rowNumber,
        String reason
) {}
