package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;

import java.time.Instant;
import java.util.UUID;

public record PeriodVersionSummary(
        UUID id,
        int versionNumber,
        PeriodStatus status,
        boolean isLatestFinalised,
        String createdBy,
        Instant createdAt,
        Instant finalisedAt
) {
    public static PeriodVersionSummary from(PeriodVersion v) {
        return new PeriodVersionSummary(
                v.getId(),
                v.getVersionNumber(),
                v.getStatus(),
                v.isLatestFinalised(),
                v.getCreatedBy(),
                v.getCreatedAt(),
                v.getFinalisedAt());
    }
}
