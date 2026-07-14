package com.cognologix.fpa.customer.dto;

import java.util.List;

public record CustomerImportResponse(
        int totalRows,
        int created,
        int updated,
        int skipped,
        List<CustomerImportRowError> errors
) {}
