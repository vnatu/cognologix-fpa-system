package com.cognologix.fpa.people.dto;

import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;

import java.util.List;
import java.util.UUID;

public record DashboardPeriodResponse(
        UUID id,
        int periodMonth,
        int periodYear,
        List<DashboardVersionResponse> versions
) {
    public static DashboardPeriodResponse from(Period period, List<PeriodVersion> versions) {
        return new DashboardPeriodResponse(
                period.getId(),
                period.getPeriodMonth(),
                period.getPeriodYear(),
                versions.stream().map(DashboardVersionResponse::from).toList());
    }

    public record DashboardVersionResponse(
            UUID id,
            int versionNumber,
            PeriodStatus status,
            boolean isLatestFinalised
    ) {
        public static DashboardVersionResponse from(PeriodVersion v) {
            return new DashboardVersionResponse(
                    v.getId(),
                    v.getVersionNumber(),
                    v.getStatus(),
                    v.isLatestFinalised());
        }
    }
}
