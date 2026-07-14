package com.cognologix.fpa.customer.dto;

public record RateCardImportRowError(
        int rowNumber,
        String customerCode,
        String rateCardName,
        String reason
) {}
