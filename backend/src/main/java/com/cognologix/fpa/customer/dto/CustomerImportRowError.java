package com.cognologix.fpa.customer.dto;

public record CustomerImportRowError(
        int rowNumber,
        String customerCode,
        String reason
) {}
