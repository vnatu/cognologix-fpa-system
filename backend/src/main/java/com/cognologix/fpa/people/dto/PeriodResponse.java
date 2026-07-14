package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodVersion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PeriodResponse(
        UUID id,
        int periodMonth,
        int periodYear,
        Instant createdAt,
        List<PeriodVersionSummary> versions
) {
    public static PeriodResponse from(Period period, List<PeriodVersion> versions) {
        return new PeriodResponse(
                period.getId(),
                period.getPeriodMonth(),
                period.getPeriodYear(),
                period.getCreatedAt(),
                versions.stream().map(PeriodVersionSummary::from).toList());
    }
}
