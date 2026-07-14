package com.cognologix.fpa.customer.dto;

import java.util.List;

public record CustomerImportConflictsResponse(
        List<String> existingCodes,
        List<String> newCodes
) {}
