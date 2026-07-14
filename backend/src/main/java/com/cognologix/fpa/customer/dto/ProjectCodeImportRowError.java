package com.cognologix.fpa.customer.dto;

public record ProjectCodeImportRowError(
        int rowNumber,
        String customerCode,
        String projectCode,
        String reason
) {}
