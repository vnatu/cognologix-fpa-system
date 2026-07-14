package com.cognologix.fpa.customer.dto;

import java.util.List;

public record RateCardImportResponse(
        int totalRows,
        int rateCardsCreated,
        int rateCardsSkipped,
        List<RateCardImportRowError> errors,
        List<RateCardImportSkipped> skipped
) {}
