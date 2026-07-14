package com.cognologix.fpa.customer.dto;

public record RateCardImportSkipped(
        String customerCode,
        String rateCardName,
        String effectiveFrom
) {}
