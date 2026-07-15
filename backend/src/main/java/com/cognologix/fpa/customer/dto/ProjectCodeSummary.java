package com.cognologix.fpa.customer.dto;

import java.util.UUID;

/** Project code summary on a rate card response (ADR-035). */
public record ProjectCodeSummary(
        UUID id,
        String projectCode,
        String description
) {}
