package com.cognologix.fpa.general.dto;

import java.util.List;

public record FxRateImportResponse(
        int totalRows,
        int created,
        int skipped,
        List<FxRateImportRowError> errors
) {}
